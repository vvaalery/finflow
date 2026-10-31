package com.finance.backend.repository;

import com.finance.backend.entity.ReferenceCashFlowItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ReferenceCashFlowItemRepository extends JpaRepository<ReferenceCashFlowItem, UUID> {}