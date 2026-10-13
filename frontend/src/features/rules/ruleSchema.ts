import { z } from 'zod';
import { SEVERITIES } from '../../types/api';

/** Mirrors the backend Bean Validation constraints on CreateRuleRequest / UpdateRuleRequest. */
export const ruleSchema = z.object({
  code: z
    .string()
    .trim()
    .regex(/^[A-Z][A-Z0-9_]{2,63}$/, '3-64 upper-case letters, digits or underscores, starting with a letter'),
  title: z.string().trim().min(3, 'At least 3 characters').max(200, 'At most 200 characters'),
  question: z.string().trim().min(10, 'At least 10 characters').max(2000, 'At most 2000 characters'),
  guidance: z.string().trim().min(10, 'At least 10 characters').max(4000, 'At most 4000 characters'),
  severity: z.enum(SEVERITIES),
  mandatory: z.boolean(),
  active: z.boolean(),
});

export type RuleFormValues = z.infer<typeof ruleSchema>;
