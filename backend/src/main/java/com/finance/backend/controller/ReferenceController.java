package com.finance.backend.controller;

import com.finance.backend.entity.ReferenceCFO;
import com.finance.backend.entity.ReferenceCashFlowItem;
import com.finance.backend.entity.ReferenceCounterparty;
import com.finance.backend.repository.ReferenceCFORepository;
import com.finance.backend.repository.ReferenceCashFlowItemRepository;
import com.finance.backend.repository.ReferenceCounterpartyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/references")
@RequiredArgsConstructor
public class ReferenceController {

    private final ReferenceCFORepository cfoRepository;
    private final ReferenceCashFlowItemRepository cashFlowItemRepository;
    private final ReferenceCounterpartyRepository counterpartyRepository;

    @GetMapping("/cfo")
    @Cacheable("referenceCfo")
    public List<ReferenceCFO> getAllCFO() {
        return cfoRepository.findAll();
    }

    @PostMapping("/cfo")
    @CacheEvict(value = "referenceCfo", allEntries = true)
    public ReferenceCFO createCFO(@RequestBody ReferenceCFO cfo) {
        return cfoRepository.save(cfo);
    }

    @PutMapping("/cfo/{id}")
    @CacheEvict(value = "referenceCfo", allEntries = true)
    public ReferenceCFO updateCFO(@PathVariable UUID id, @RequestBody ReferenceCFO cfoData) {
        ReferenceCFO cfo = cfoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("ЦФО не найден"));
        cfo.setName(cfoData.getName());
        cfo.setDescription(cfoData.getDescription());
        return cfoRepository.save(cfo);
    }

    @DeleteMapping("/cfo/{id}")
    @CacheEvict(value = "referenceCfo", allEntries = true)
    public void deleteCFO(@PathVariable UUID id) {
        cfoRepository.deleteById(id);
    }

    @GetMapping("/cash-flow-items")
    @Cacheable("referenceCashFlowItems")
    public List<ReferenceCashFlowItem> getAllCashFlowItems() {
        return cashFlowItemRepository.findAll();
    }

    @PostMapping("/cash-flow-items")
    @CacheEvict(value = "referenceCashFlowItems", allEntries = true)
    public ReferenceCashFlowItem createCashFlowItem(@RequestBody ReferenceCashFlowItem item) {
        return cashFlowItemRepository.save(item);
    }

    @PutMapping("/cash-flow-items/{id}")
    @CacheEvict(value = "referenceCashFlowItems", allEntries = true)
    public ReferenceCashFlowItem updateCashFlowItem(@PathVariable UUID id, @RequestBody ReferenceCashFlowItem itemData) {
        ReferenceCashFlowItem item = cashFlowItemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Статья ДДС не найдена"));
        item.setName(itemData.getName());
        item.setType(itemData.getType());
        item.setDescription(itemData.getDescription());
        return cashFlowItemRepository.save(item);
    }

    @DeleteMapping("/cash-flow-items/{id}")
    @CacheEvict(value = "referenceCashFlowItems", allEntries = true)
    public void deleteCashFlowItem(@PathVariable UUID id) {
        cashFlowItemRepository.deleteById(id);
    }

    @GetMapping("/counterparties")
    @Cacheable("referenceCounterparties")
    public List<ReferenceCounterparty> getAllCounterparties() {
        return counterpartyRepository.findAll();
    }

    @PostMapping("/counterparties")
    @CacheEvict(value = "referenceCounterparties", allEntries = true)
    public ReferenceCounterparty createCounterparty(@RequestBody ReferenceCounterparty counterparty) {
        return counterpartyRepository.save(counterparty);
    }

    @PutMapping("/counterparties/{id}")
    @CacheEvict(value = "referenceCounterparties", allEntries = true)
    public ReferenceCounterparty updateCounterparty(@PathVariable UUID id, @RequestBody ReferenceCounterparty counterpartyData) {
        ReferenceCounterparty counterparty = counterpartyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Контрагент не найден"));
        counterparty.setName(counterpartyData.getName());
        counterparty.setTaxId(counterpartyData.getTaxId());
        counterparty.setAddress(counterpartyData.getAddress());
        counterparty.setPhone(counterpartyData.getPhone());
        counterparty.setEmail(counterpartyData.getEmail());
        return counterpartyRepository.save(counterparty);
    }

    @DeleteMapping("/counterparties/{id}")
    @CacheEvict(value = "referenceCounterparties", allEntries = true)
    public void deleteCounterparty(@PathVariable UUID id) {
        counterpartyRepository.deleteById(id);
    }
}