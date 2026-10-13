import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { deleteContract, getContract, listContracts, reprocessContract } from '../../api/contracts';
import { queryKeys } from '../../api/queryKeys';
import { POLL_INTERVAL_MS } from '../../config';
import type { ContractListParams } from '../../types/api';
import { isContractInFlight } from '../../types/labels';

/**
 * Contract list. It polls only while at least one visible contract is QUEUED or PROCESSING,
 * and stops automatically once everything is READY or FAILED.
 */
export function useContractList(params: ContractListParams) {
  return useQuery({
    queryKey: queryKeys.contracts(params),
    queryFn: () => listContracts(params),
    placeholderData: (previous) => previous,
    refetchInterval: (query) =>
      query.state.data?.content.some((contract) => isContractInFlight(contract.status)) ? POLL_INTERVAL_MS : false,
  });
}

export function useContract(id: string) {
  return useQuery({
    queryKey: queryKeys.contract(id),
    queryFn: () => getContract(id),
    refetchInterval: (query) => (query.state.data && isContractInFlight(query.state.data.status) ? POLL_INTERVAL_MS : false),
  });
}

export function useReprocessContract() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ id, force }: { id: string; force?: boolean }) => reprocessContract(id, force),
    onSuccess: (contract) => {
      client.setQueryData(queryKeys.contract(contract.id), contract);
      void client.invalidateQueries({ queryKey: queryKeys.contractsAll });
      void client.invalidateQueries({ queryKey: queryKeys.dashboard });
    },
  });
}

export function useDeleteContract() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => deleteContract(id),
    onSuccess: (_, id) => {
      client.removeQueries({ queryKey: queryKeys.contract(id) });
      void client.invalidateQueries({ queryKey: queryKeys.contractsAll });
      void client.invalidateQueries({ queryKey: queryKeys.dashboard });
    },
  });
}
