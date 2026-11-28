
package com.finance.internal.service;

import com.finance.backend.dto.response.ForecastResponse;
import com.finance.backend.entity.Account;
import com.finance.backend.entity.Company;
import com.finance.backend.entity.PaymentCalendar;
import com.finance.backend.entity.PaymentRequest;
import com.finance.backend.entity.User;
import com.finance.backend.repository.AccountRepository;
import com.finance.backend.repository.CompanyRepository;
import com.finance.backend.repository.PaymentCalendarRepository;
import com.finance.backend.repository.PaymentRequestRepository;
import com.finance.backend.service.ForecastService;
import com.finance.internal.domain.PaymentStatus;
import com.finance.internal.dto.GraphQLInput.PaymentInput;
import com.finance.internal.dto.GraphQLInput.PaymentRequestInput;
import com.finance.internal.dto.GraphQLView.MutationResult;
import com.finance.internal.dto.GraphQLView.PaymentRequestView;
import com.finance.internal.dto.GraphQLView.PaymentView;
import com.finance.internal.security.AccessPolicy;
import com.finance.kafka.event.EventTypes;
import com.finance.kafka.outbox.OutboxEventService;
import com.finance.paymentservice.client.CompanyServiceGateway;
import com.finance.paymentservice.client.CompanyServiceGateway.AccountDto;
import com.finance.paymentservice.client.CompanyServiceGateway.BalanceCheckDto;
import com.finance.paymentservice.service.PaymentValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static com.finance.internal.security.AccessPolicy.ADMIN;
import static com.finance.internal.security.AccessPolicy.DIRECTOR;
import static com.finance.internal.security.AccessPolicy.MANAGER;

@Slf4j
@Service
@RequiredArgsConstructor
public class InternalPaymentService {
    private static final String BASE_CURRENCY = "BYN";

    private final PaymentCalendarRepository payments;
    private final PaymentRequestRepository requests;
    private final CompanyRepository companies;
    private final AccountRepository accounts;
    private final ForecastService forecastService;
    private final PaymentValidationService validation;
    private final CompanyServiceGateway companyService;
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
        log.info("Платёж создан вручную: paymentId={}, companyId={}", saved.getId(), c.getId());
        return Views.result(saved.getId(), "Платёж добавлен");
    }

    @Transactional
    public MutationResult updatePayment(UUID id, PaymentInput in) {
        User u = policy.currentUser();
        policy.requireRole(u, ADMIN, MANAGER);
        PaymentCalendar p = payments.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Платёж не найден"));
        policy.requireWriteCompany(u, p.getCompany());

        if (!p.getCompany().getId().equals(in.companyId())) {
            throw new IllegalArgumentException("Нельзя перенести платёж в другую компанию");
        }

        String current = PaymentStatus.current(p.getStatus());
        if (PaymentStatus.isTerminal(current)) {
            throw new IllegalArgumentException("Платёж в статусе " + current + " изменять нельзя");
        }

        String target = in.status() == null || in.status().isBlank()
                ? current : PaymentStatus.normalize(in.status());
        PaymentStatus.checkPaymentTransition(current, target);

        UUID linkedRequestId = p.getPaymentRequestId();
        boolean statusChanges = !target.equals(current);

        if (linkedRequestId != null && statusChanges && !PaymentStatus.PAID.equals(target)) {
            throw new IllegalArgumentException("Платёж создан по заявке: статусы APPROVED, REJECTED и CANCELLED меняются только через заявку");
        }

        boolean decision = PaymentStatus.APPROVED.equals(target) || PaymentStatus.REJECTED.equals(target);
        if (decision && statusChanges && !(policy.isAdmin(u) || policy.isDirector(u))) {
            throw new AccessDeniedException("Утверждать и отклонять платежи могут только руководитель или администратор");
        }

        boolean detailsChanged = !p.getTitle().equals(in.title())
                || p.getAmount().compareTo(in.amount()) != 0
                || !p.getDueDate().equals(in.dueDate());

        if (PaymentStatus.APPROVED.equals(current) && detailsChanged) {
            throw new IllegalArgumentException("Утверждённый платёж: изменять название, сумму и срок нельзя");
        }

        fill(p, in);
        p.setStatus(target);
        payments.save(p);

        if (linkedRequestId != null && PaymentStatus.PAID.equals(target) && statusChanges) {
            requests.findForUpdate(linkedRequestId).ifPresent(r -> {
                if (PaymentStatus.APPROVED.equals(PaymentStatus.current(r.getStatus()))) {
                    r.setStatus(PaymentStatus.PAID);
                    requests.save(r);
                    log.info("Заявка {} переведена в PAID вместе с платежом {}", r.getId(), p.getId());
                }
            });
        }

        return Views.result(id, "Платёж обновлён");
    }

    @Transactional
    public MutationResult deletePayment(UUID id) {
        User u = policy.currentUser();
        policy.requireRole(u, ADMIN);
        PaymentCalendar p = payments.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Платёж не найден"));

        if (PaymentStatus.PAID.equals(PaymentStatus.current(p.getStatus()))) {
            throw new IllegalArgumentException("Оплаченный платёж удалять нельзя");
        }

        if (p.getPaymentRequestId() != null) {
            throw new IllegalArgumentException("Платёж создан по заявке: удалять его нельзя");
        }

        payments.delete(p);
        return Views.result(id, "Платёж удалён");
    }

    @Transactional(readOnly = true)
    public List<PaymentRequestView> paymentRequests() {
        User u = policy.currentUser();
        return requests.findAllByOrderByDateDesc().stream()
                .filter(r -> visible(u, r))
                .map(Views::request)
                .toList();
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
        AccountDto owned = companyService.getAccount(in.accountId())
                .orElseThrow(() -> new NoSuchElementException("Счёт не найден"));

        if (!owned.active()) {
            throw new IllegalArgumentException("Счёт неактивен");
        }

        Account account = accounts.findById(in.accountId())
                .orElseThrow(() -> new NoSuchElementException("Счёт не найден"));

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
        outbox.enqueue(EventTypes.PAYMENT_REQUEST_CREATED, saved.getId(), c.getId(),
                "paymentRequestId", saved.getId(), "accountId", account.getId(), "title", saved.getTitle(),
                "amount", plain(saved.getAmount()), "status", saved.getStatus(), "date", saved.getDate());

        log.info("Платёжная заявка создана: requestId={}, companyId={}, accountId={}, amount={}",
                saved.getId(), c.getId(), account.getId(), saved.getAmount());

        return Views.result(saved.getId(), "Платёжная заявка создана");
    }

    @Transactional
    public MutationResult approvePaymentRequest(UUID id) {
        User u = policy.currentUser();
        policy.requireRole(u, ADMIN, DIRECTOR);
        PaymentRequest r = lockedRequest(id);
        Account account = r.getAccount();

        if (account == null) {
            throw new IllegalArgumentException("В заявке не указан счёт: утвердить её нельзя");
        }

        policy.requireReadCompany(u, account.getCompany());
        PaymentStatus.checkRequestTransition(PaymentStatus.current(r.getStatus()), PaymentStatus.APPROVED);

        UUID companyId = account.getCompany().getId();
        validation.validateAccountOwnership(account.getId(), companyId);
        checkProjectedBalance(account, r);

        r.setStatus(PaymentStatus.APPROVED);
        requests.save(r);

        PaymentCalendar linked = payments.findByPaymentRequestId(r.getId()).orElse(null);
        String message;

        if (linked != null) {
            if (!companyId.equals(linked.getCompany().getId())) {
                throw new IllegalStateException("Календарный платёж связан с заявкой другой компании");
            }

            String linkedStatus = PaymentStatus.current(linked.getStatus());

            if (PaymentStatus.PENDING.equals(linkedStatus)) {
                linked.setStatus(PaymentStatus.APPROVED);
                payments.save(linked);
            } else if (!PaymentStatus.APPROVED.equals(linkedStatus)) {
                throw new IllegalArgumentException("Связанный платёж в статусе " + linkedStatus + ": утвердить заявку нельзя");
            }

            message = "Заявка утверждена, связанный платёж обновлён";
        } else {
            PaymentCalendar p = new PaymentCalendar();
            p.setCompany(account.getCompany());
            p.setPaymentRequestId(r.getId());
            p.setTitle(r.getTitle());
            p.setDescription(r.getDescription());
            p.setAmount(r.getAmount());
            p.setDueDate(r.getDate());
            p.setStatus(PaymentStatus.APPROVED);
            p.setCategory(r.getCategory());
            p.setCounterparty(r.getCounterparty());
            p.setCreatedBy(u);

            linked = payments.save(p);
            enqueueCalendar(linked);
            message = "Заявка утверждена, платёж добавлен в календарь";
        }

        outbox.enqueue(EventTypes.PAYMENT_REQUEST_APPROVED, r.getId(), companyId,
                "paymentRequestId", r.getId(), "paymentCalendarId", linked.getId(), "accountId", account.getId(),
                "title", r.getTitle(), "amount", plain(r.getAmount()), "date", r.getDate());

        log.info("Заявка утверждена: requestId={}, paymentId={}, companyId={}, approvedBy={}",
                r.getId(), linked.getId(), companyId, u.getId());

        return Views.result(r.getId(), message);
    }

    @Transactional
    public MutationResult rejectPaymentRequest(UUID id) {
        User u = policy.currentUser();
        policy.requireRole(u, ADMIN, DIRECTOR);
        PaymentRequest r = lockedRequest(id);

        if (r.getAccount() != null) {
            policy.requireReadCompany(u, r.getAccount().getCompany());
        }

        PaymentStatus.checkRequestTransition(PaymentStatus.current(r.getStatus()), PaymentStatus.REJECTED);
        r.setStatus(PaymentStatus.REJECTED);
        requests.save(r);

        payments.findByPaymentRequestId(r.getId()).ifPresent(p -> {
            if (PaymentStatus.PENDING.equals(PaymentStatus.current(p.getStatus()))) {
                p.setStatus(PaymentStatus.REJECTED);
                payments.save(p);
            }
        });

        UUID companyId = r.getAccount() == null ? null : r.getAccount().getCompany().getId();
        outbox.enqueue(EventTypes.PAYMENT_REQUEST_REJECTED, r.getId(), companyId,
                "paymentRequestId", r.getId(), "accountId", r.getAccount() == null ? null : r.getAccount().getId(),
                "title", r.getTitle(), "amount", plain(r.getAmount()));

        log.info("Заявка отклонена: requestId={}, rejectedBy={}", r.getId(), u.getId());
        return Views.result(r.getId(), "Заявка отклонена");
    }

    private void checkProjectedBalance(Account account, PaymentRequest r) {
        UUID companyId = account.getCompany().getId();

        BigDecimal committed = payments.findByCompanyIdAndStatus(companyId, PaymentStatus.APPROVED).stream()
                .map(PaymentCalendar::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal projectedNet = BigDecimal.ZERO;

        if (BASE_CURRENCY.equalsIgnoreCase(account.getCurrency())) {
            try {
                ForecastResponse latest = forecastService.latestOrNull(companyId);
                if (latest != null && latest.getNetCashFlow() != null) {
                    projectedNet = latest.getNetCashFlow().min(BigDecimal.ZERO);
                }
            } catch (RestClientException e) {
                log.warn("Forecast Service недоступен, остаток проверен без прогноза: {}", e.getMessage());
            }
        }

        BalanceCheckDto check = companyService.checkProjectedBalance(
                companyId, account.getId(), r.getAmount(), committed, projectedNet);

        if (!check.sufficient()) {
            log.warn("Согласование заявки {} отклонено проверкой остатка: {}", r.getId(), check.message());
            throw new IllegalArgumentException("Нельзя согласовать заявку: " + check.message());
        }
    }

    private PaymentRequest lockedRequest(UUID id) {
        return requests.findForUpdate(id)
                .orElseThrow(() -> new NoSuchElementException("Заявка не найдена"));
    }

    private boolean visible(User u, PaymentRequest r) {
        if (policy.canReadAll(u)) return true;
        return policy.isManager(u) && r.getAccount() != null && policy.owns(u, r.getAccount().getCompany());
    }

    private Company company(UUID id) {
        return companies.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Компания не найдена"));
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
        outbox.enqueue(EventTypes.PAYMENT_CALENDAR_CREATED, p.getId(), p.getCompany().getId(),
                "paymentCalendarId", p.getId(), "paymentRequestId", p.getPaymentRequestId(), "title", p.getTitle(),
                "amount", plain(p.getAmount()), "status", p.getStatus(), "dueDate", p.getDueDate());
    }

    private static String plain(BigDecimal v) {
        return v == null ? null : v.toPlainString();
    }
}
