import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Pencil, Plus } from 'lucide-react';
import { useState } from 'react';
import { queryKeys } from '../../api/queryKeys';
import { createRule, listRules, setRuleActive, updateRule } from '../../api/rules';
import { Card, EmptyState, PageHeader, TableSkeleton } from '../../components/Feedback';
import { buttonClasses } from '../../components/styles';
import { SeverityBadge } from '../../components/StatusBadges';
import { useToast } from '../../notifications/toastContext';
import type { Rule } from '../../types/api';
import { RuleFormModal } from './RuleFormModal';
import type { RuleFormValues } from './ruleSchema';

export function RulesPage() {
  const client = useQueryClient();
  const toast = useToast();
  const [editing, setEditing] = useState<Rule | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const rules = useQuery({ queryKey: queryKeys.rules, queryFn: () => listRules(false) });

  const toggle = useMutation({
    mutationFn: ({ id, active }: { id: number; active: boolean }) => setRuleActive(id, active),
    onSuccess: () => client.invalidateQueries({ queryKey: queryKeys.rules }),
  });

  const save = async (values: RuleFormValues) => {
    if (editing) {
      const { code: _code, ...update } = values;
      await updateRule(editing.id, update);
      toast.notify('success', `Rule ${editing.code} updated`);
    } else {
      await createRule(values);
      toast.notify('success', `Rule ${values.code} created`);
    }
    await client.invalidateQueries({ queryKey: queryKeys.rules });
    setModalOpen(false);
  };

  return (
    <>
      <PageHeader
        title="Compliance rules"
        subtitle="The policy rule set the audit agent evaluates. Severity drives the risk-score weight."
        actions={
          <button
            type="button"
            className={buttonClasses.primary}
            onClick={() => {
              setEditing(null);
              setModalOpen(true);
            }}
          >
            <Plus className="h-4 w-4" aria-hidden /> New rule
          </button>
        }
      />
      <Card>
        {rules.isLoading ? (
          <TableSkeleton rows={8} />
        ) : !rules.data || rules.data.length === 0 ? (
          <EmptyState title="No rules defined" />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-surface-border text-xs uppercase text-ink-faint">
                <tr>
                  <th className="py-2 pr-4">Rule</th>
                  <th className="py-2 pr-4">Severity</th>
                  <th className="py-2 pr-4">Mandatory</th>
                  <th className="py-2 pr-4">Active</th>
                  <th className="py-2 text-right">Edit</th>
                </tr>
              </thead>
              <tbody>
                {rules.data.map((rule) => (
                  <tr key={rule.id} className={`border-b border-surface-border last:border-0 ${rule.active ? '' : 'opacity-60'}`}>
                    <td className="py-3 pr-4">
                      <div className="font-medium">{rule.title}</div>
                      <div className="font-mono text-xs text-ink-faint">{rule.code}</div>
                      <div className="mt-1 max-w-xl text-xs text-ink-muted">{rule.question}</div>
                    </td>
                    <td className="py-3 pr-4">
                      <SeverityBadge severity={rule.severity} />
                    </td>
                    <td className="py-3 pr-4 text-ink-muted">{rule.mandatory ? 'Yes' : 'No'}</td>
                    <td className="py-3 pr-4">
                      <label className="inline-flex cursor-pointer items-center gap-2">
                        <input
                          type="checkbox"
                          checked={rule.active}
                          disabled={toggle.isPending}
                          onChange={(event) => toggle.mutate({ id: rule.id, active: event.target.checked })}
                          aria-label={`${rule.active ? 'Deactivate' : 'Activate'} ${rule.code}`}
                        />
                        <span className="text-xs text-ink-muted">{rule.active ? 'Active' : 'Inactive'}</span>
                      </label>
                    </td>
                    <td className="py-3 text-right">
                      <button
                        type="button"
                        className={buttonClasses.ghost}
                        aria-label={`Edit ${rule.code}`}
                        onClick={() => {
                          setEditing(rule);
                          setModalOpen(true);
                        }}
                      >
                        <Pencil className="h-4 w-4" aria-hidden />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
      <RuleFormModal open={modalOpen} rule={editing} onClose={() => setModalOpen(false)} onSubmit={save} />
    </>
  );
}
