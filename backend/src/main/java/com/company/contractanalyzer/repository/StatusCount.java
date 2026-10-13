package com.company.contractanalyzer.repository;

import com.company.contractanalyzer.enums.ContractStatus;

/** JPQL constructor projection: number of contracts per status. */
public record StatusCount(ContractStatus status, long count) {
}
