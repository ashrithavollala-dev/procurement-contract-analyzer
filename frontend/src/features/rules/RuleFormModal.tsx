import { zodResolver } from '@hookform/resolvers/zod';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { ApiError } from '../../api/errors';
import { buttonClasses, inputClasses } from '../../components/styles';
import { Modal } from '../../components/Modal';
import { friendlyMessage } from '../../notifications/errorMessages';
import { SEVERITIES, type Rule } from '../../types/api';
import { SEVERITY_LABELS } from '../../types/labels';
import { ruleSchema, type RuleFormValues } from './ruleSchema';

const EMPTY: RuleFormValues = {
  code: '',
  title: '',
  question: '',
  guidance: '',
  severity: 'MEDIUM',
  mandatory: false,
  active: true,
};

/** Create or edit a rule. Server-side validation errors are mapped back onto the form fields. */
export function RuleFormModal({ open, rule, onClose, onSubmit }: {
  open: boolean;
  rule: Rule | null;
  onClose: () => void;
  onSubmit: (values: RuleFormValues) => Promise<void>;
}) {
  const [formError, setFormError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<RuleFormValues>({ resolver: zodResolver(ruleSchema), defaultValues: EMPTY });

  useEffect(() => {
    if (open) {
      setFormError(null);
      reset(rule ? { ...rule } : EMPTY);
    }
  }, [open, rule, reset]);

  const submit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await onSubmit(values);
    } catch (error) {
      if (error instanceof ApiError && error.code === 'VALIDATION_FAILED') {
        const fields = error.fieldErrors();
        Object.entries(fields).forEach(([field, message]) => {
          if (field in EMPTY) setError(field as keyof RuleFormValues, { message });
        });
        if (Object.keys(fields).length === 0) setFormError(friendlyMessage(error));
      } else {
        setFormError(error instanceof ApiError ? friendlyMessage(error) : 'Saving failed.');
      }
    }
  });

  const field = (name: keyof RuleFormValues) =>
    errors[name] ? <p className="mt-1 text-xs text-status-danger">{errors[name]?.message}</p> : null;

  return (
    <Modal
      open={open}
      title={rule ? `Edit ${rule.code}` : 'New compliance rule'}
      onClose={onClose}
      footer={
        <>
          <button type="button" className={buttonClasses.secondary} onClick={onClose}>
            Cancel
          </button>
          <button type="submit" form="rule-form" className={buttonClasses.primary} disabled={isSubmitting}>
            {isSubmitting ? 'Saving...' : 'Save rule'}
          </button>
        </>
      }
    >
      <form id="rule-form" onSubmit={submit} className="space-y-3" noValidate>
        <div>
          <label htmlFor="rule-code" className="mb-1 block text-sm font-medium">
            Code
          </label>
          <input id="rule-code" className={`${inputClasses} font-mono`} disabled={rule !== null} {...register('code')} />
          {field('code')}
        </div>
        <div>
          <label htmlFor="rule-title" className="mb-1 block text-sm font-medium">
            Title
          </label>
          <input id="rule-title" className={inputClasses} {...register('title')} />
          {field('title')}
        </div>
        <div>
          <label htmlFor="rule-question" className="mb-1 block text-sm font-medium">
            Question to verify
          </label>
          <textarea id="rule-question" rows={3} className={inputClasses} {...register('question')} />
          {field('question')}
        </div>
        <div>
          <label htmlFor="rule-guidance" className="mb-1 block text-sm font-medium">
            What a compliant clause looks like
          </label>
          <textarea id="rule-guidance" rows={4} className={inputClasses} {...register('guidance')} />
          {field('guidance')}
        </div>
        <div className="flex flex-wrap items-center gap-5">
          <div>
            <label htmlFor="rule-severity" className="mb-1 block text-sm font-medium">
              Severity
            </label>
            <select id="rule-severity" className={`${inputClasses} w-40`} {...register('severity')}>
              {SEVERITIES.map((s) => (
                <option key={s} value={s}>
                  {SEVERITY_LABELS[s]}
                </option>
              ))}
            </select>
          </div>
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" {...register('mandatory')} /> Mandatory
          </label>
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" {...register('active')} /> Active
          </label>
        </div>
        {formError && (
          <p role="alert" className="text-sm text-status-danger">
            {formError}
          </p>
        )}
      </form>
    </Modal>
  );
}
