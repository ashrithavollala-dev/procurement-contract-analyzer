package com.company.contractanalyzer.mapper;

import com.company.contractanalyzer.dto.response.ContractResponse;
import com.company.contractanalyzer.entity.Contract;
import org.mapstruct.Mapper;

/** Entity -> DTO only; entities never cross the API boundary. */
@Mapper
public interface ContractMapper {

    ContractResponse toResponse(Contract contract);
}
