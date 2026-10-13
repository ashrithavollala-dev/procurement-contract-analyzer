package com.company.contractanalyzer.mapper;

import com.company.contractanalyzer.ai.RuleSpec;
import com.company.contractanalyzer.dto.request.CreateRuleRequest;
import com.company.contractanalyzer.dto.request.UpdateRuleRequest;
import com.company.contractanalyzer.dto.response.RuleResponse;
import com.company.contractanalyzer.entity.ComplianceRule;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper
public interface ComplianceRuleMapper {

    RuleResponse toResponse(ComplianceRule rule);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "active", source = "active", defaultValue = "true")
    ComplianceRule toEntity(CreateRuleRequest request);

    /** Codes are immutable: findings and API clients refer to rules by code. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "code", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void update(UpdateRuleRequest request, @MappingTarget ComplianceRule rule);

    RuleSpec toSpec(ComplianceRule rule);
}
