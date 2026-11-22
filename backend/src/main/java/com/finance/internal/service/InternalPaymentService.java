package com.finance.internal.service;

import com.finance.backend.entity.Account;
import com.finance.backend.entity.Company;
import com.finance.backend.entity.PaymentCalendar;
import com.finance.backend.entity.PaymentRequest;
import com.finance.backend.entity.User;
import com.finance.backend.repository.AccountRepository;
import com.finance.backend.repository.CompanyRepository;
import com.finance.backend.repository.PaymentCalendarRepository;
import com.finance.backend.repository.PaymentRequestRepository;
import com.finance.internal.domain.PaymentStatus;
import com.finance.internal.dto.GraphQLInput.PaymentInput;
import com.finance.internal.dto.GraphQLInput.PaymentRequestInput;
import com.finance.internal.dto.GraphQLView.MutationResult;
import com.finance.internal.dto.GraphQLView.PaymentRequestView;
import com.finance.internal.dto.GraphQLView.PaymentView;
import com.finance.internal.security.AccessPolicy;
import com.finance.kafka.event.PaymentCalendarCreatedEvent;
import com.finance.kafka.event.PaymentRequestCreatedEvent;
import com.finance.kafka.outbox.OutboxEventService;
import com.finance.paymentservice.service.PaymentValidationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static com.finance.internal.security.AccessPolicy.ADMIN;
import static com.finance.internal.security.AccessPolicy.DIRECTOR;
import static com.finance.internal.security.AccessPolicy.MANAGER;

@Service
@RequiredArgsConstructor
public class InternalPaymentService {
    private final PaymentCalendarRepository payments;
    private final PaymentRequestRepository requests;
    private final CompanyRepository companies;
    private final AccountRepository accounts;
    private final PaymentValidationService validation;
    private final OutboxEventService outbox;
    private final AccessPolicy policy;

    @Transactional(readOnly = true)
    public List<PaymentView> payments(UUID companyId) {
        User u = policy.currentUser();
        Company c = company(companyId);
        policy.requireReadCompany(u, c);
        return payments.findByCompanyId(c.getId()).stream().map(Views::payment).toList();
    }

    @Transactional
    public MutationResult createPayment(PaymentInput in) {
        User u = policy.currentUser();
        policy.requireRole(u, ADMIN, MANAGER);
        Company c = company(in.companyId());
        policy.requireWriteCompany(u, c);
        if (in.status() != null && !in.status().isBlank()
                && !PaymentStatus.PENDING.equals(PaymentStatus.normalize(in.status()))) {
            throw new IllegalArgumentException("Платёж создаётся в статусе PENDING; дальше статус меняется только по допустимым переходам");
        }
        PaymentCalendar p = new PaymentCalendar();
        p.setCompany(c);
        fill(p, in);
        p.setStatus(PaymentStatus.PENDING);
        p.setCreatedBy(u);
        PaymentCalendar saved = payments.save(p);
        enqueueCalendar(saved);
        return Views.result(saved.getId(), "Платёж добавлен");
    }

    @Transactional
    public MutationResult updatePayment(UUID id, PaymentInput in) {
        User u = policy.currentUser();
        policy.requireRole(u, ADMIN, MANAGER);
        PaymentCalendar p = payments.findById(id).orElseThrow(() -> new NoSuchElementException("Платёж не найден"));
        policy.requireWriteCompany(u, p.getCompany());
        if (!p.getCompany().getId().equals(in.companyId())) {
            throw new IllegalArgumentException("Нельзя перенести платёж в другую компанию");
        }
        String current = PaymentStatus.current(p.getStatus());
        if (PaymentStatus.isTerminal(current)) {
            throw new IllegalArgumentException("Платёж в статусе " + current + " изменять нельзя");
        }
        String target = in.status() == null || in.status().isBlank() ? current : PaymentStatus.normalize(in.status());
        PaymentStatus.checkPaymentTransition(current, target);

        boolean decision = PaymentStatus.APPROVED.equals(target) || PaymentStatus.REJECTED.equals(target);
        if (decision && !target.equals(current) && !(policy.isAdmin(u) || policy.isDirector(u))) {
            throw new AccessDeniedException("Утверждать и отклонять платежи могут только руководитель или администратор");
        }
        boolean detailsChanged = !p.getTitle().equals(in.title())
                || p.getAmount().compareTo(in.amount()) != 0 || !p.getDueDate().equals(in.dueDate());
        if (PaymentStatus.APPROVED.equals(current) && detailsChanged) {
            throw new IllegalArgumentException("Утверждённый платёж: изменять название, сумму и срок нельзя");
        }
        fill(p, in);
        p.setStatus(target);
        payments.save(p);
        return Views.result(id, "Платёж обновлён");
    }

    @Transactional
    public MutationResult deletePayment(UUID id) {
        User u = policy.currentUser();
        policy.requireRole(u, ADMIN);
        PaymentCalendar p = payments.findById(id).orElseThrow(() -> new NoSuchElementException("Платёж не найден"));
        if (PaymentStatus.PAID.equals(PaymentStatus.current(p.getStatus()))) {
            throw new IllegalArgumentException("Оплаченный платёж удалять нельзя");
        }
        payments.delete(p);
        return Views.result(id, "Платёж удалён");
    }

    @Transactional(readOnly = true)
    public List<PaymentRequestView> paymentRequests() {
        User u = policy.currentUser();
        return requests.findAllByOrderByDateDesc().stream().filter(r -> visible(u, r)).map(Views::request).toList();
    }

    @Transactional(readOnly = true)
    public PaymentRequestView paymentRequest(UUID id) {
        User u = policy.currentUser();
        return requests.findById(id).map(r -> {
            if (!visible(u, r)) throw new AccessDeniedException("Нет доступа к этой заявке");
            return Views.request(r);
        }).orElse(null);
    }

    @Transactional
    public MutationResult createPaymentRequest(PaymentRequestInput in) {
        User u = policy.currentUser();
        policy.requireRole(u, ADMIN, MANAGER);
        Company c = company(in.companyId());
        policy.requireWriteCompany(u, c);
        validation.validateAccountOwnership(in.accountId(), in.companyId());
        Account account = accounts.findById(in.accountId()).orElseThrow(() -> new NoSuchElementException("Счёт не найден"));
        if (!account.getCompany().getId().equals(c.getId())) {
            throw new IllegalArgumentException("Счёт не принадлежит указанной компании");
        }
        PaymentRequest r = new PaymentRequest();
        r.setAccount(account);
        r.setTitle(in.title());
        r.setAmount(in.amount().setScale(2, RoundingMode.HALF_UP));
        r.setDate(in.date());
        r.setStatus(PaymentStatus.PENDING);
        r.setRequester(u.getFullName());
        r.setCounterparty(in.counterparty());
        r.setCategory(in.category());
        r.setDescription(in.description());
        PaymentRequest saved = requests.save(r);
        outbox.enqueue(new PaymentRequestCreatedEvent(saved.getId(), saved.getTitle(), saved.getAmount(), saved.getStatus(), saved.getDate()));
        return Views.result(saved.getId(), "Платёжная заявка создана");
    }

    @Transactional
    public MutationResult approvePaymentRequest(UUID id) {
        User u = policy.currentUser();
        policy.requireRole(u, ADMIN, DIRECTOR);
        PaymentRequest r = lockedRequest(id);
        Account account = r.getAccount();
        if (account == null) throw new IllegalArgumentException("В заявке не указан счёт: утвердить её нельзя");
        policy.requireReadCompany(u, account.getCompany());
        PaymentStatus.checkRequestTransition(PaymentStatus.current(r.getStatus()), PaymentStatus.APPROVED);

        r.setStatus(PaymentStatus.APPROVED);
        requests.save(r);

        PaymentCalendar p = new PaymentCalendar();
        p.setCompany(account.getCompany());
        p.setTitle(r.getTitle());
        p.setDescription(r.getDescription());
        p.setAmount(r.getAmount());
        p.setDueDate(r.getDate());
        p.setStatus(PaymentStatus.APPROVED);
        p.setCategory(r.getCategory());
        p.setCounterparty(r.getCounterparty());
        p.setCreatedBy(u);
        enqueueCalendar(payments.save(p));
        return Views.result(r.getId(), "Заявка утверждена, платёж добавлен в календарь");
    }

    @Transactional
    public MutationResult rejectPaymentRequest(UUID id) {
        User u = policy.currentUser();
        policy.requireRole(u, ADMIN, DIRECTOR);
        PaymentRequest r = lockedRequest(id);
        if (r.getAccount() != null) policy.requireReadCompany(u, r.getAccount().getCompany());
        PaymentStatus.checkRequestTransition(PaymentStatus.current(r.getStatus()), PaymentStatus.REJECTED);
        r.setStatus(PaymentStatus.REJECTED);
        requests.save(r);
        return Views.result(r.getId(), "Заявка отклонена");
    }

    private PaymentRequest lockedRequest(UUID id) {
        return requests.findForUpdate(id).orElseThrow(() -> new NoSuchElementException("Заявка не найдена"));
    }

    private boolean visible(User u, PaymentRequest r) {
        if (policy.canReadAll(u)) return true;
        return policy.isManager(u) && r.getAccount() != null && policy.owns(u, r.getAccount().getCompany());
    }

    private Company company(UUID id) {
        return companies.findById(id).orElseThrow(() -> new NoSuchElementException("Компания не найдена"));
    }

    private void fill(PaymentCalendar p, PaymentInput in) {
        p.setTitle(in.title());
        p.setDescription(in.description());
        p.setAmount(in.amount().setScale(2, RoundingMode.HALF_UP));
        p.setDueDate(in.dueDate());
        p.setCategory(in.category());
        p.setCounterparty(in.counterparty());
    }

    private void enqueueCalendar(PaymentCalendar p) {
        outbox.enqueue(new PaymentCalendarCreatedEvent(p.getId(), p.getCompany().getId(), p.getTitle(), p.getAmount(), p.getStatus(), p.getDueDate()));
    }
}
