package com.finance.backend.repository;

import com.finance.backend.entity.ReferenceCFO;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ReferenceCFORepository extends JpaRepository<ReferenceCFO, UUID> {}