package com.finance.backend.repository;

import com.finance.backend.entity.ReferenceCounterparty;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ReferenceCounterpartyRepository extends JpaRepository<ReferenceCounterparty, UUID> {}