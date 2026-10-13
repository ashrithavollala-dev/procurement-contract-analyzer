-- V2: twelve procurement compliance rules (fictional policy written for this demo).
-- question = what the agent must verify; guidance = what a compliant clause looks like.

INSERT INTO compliance_rules (code, title, question, guidance, severity, mandatory, active, created_at, updated_at) VALUES
('PAYMENT_TERMS',
 'Payment terms',
 'Does the contract define when invoices are payable (a fixed number of days after receipt of a valid invoice), the invoicing currency, and how disputed invoice amounts are handled?',
 'Compliant: invoices payable net 30 to net 60 days after receipt of a valid invoice, currency stated, and a disputed-amount procedure under which undisputed portions are still paid. Partial: payment period stated but no dispute procedure or currency. Non-compliant: payment in advance of delivery, payment terms under 15 days, or late-payment interest above 1.5% per month.',
 'HIGH', TRUE, TRUE, now(), now()),

('TERMINATION_CONVENIENCE',
 'Termination for convenience',
 'Can the customer terminate the agreement for convenience (without cause) by giving written notice, and what notice period and termination fees apply?',
 'Compliant: the customer may terminate for convenience on 30 to 90 days written notice, paying only for services delivered up to the termination date and no penalty fee. Partial: right exists but notice exceeds 90 days or a modest wind-down fee applies. Non-compliant: no convenience termination right for the customer, or termination requires paying all remaining fees.',
 'MEDIUM', FALSE, TRUE, now(), now()),

('LIABILITY_CAP',
 'Limitation of liability cap',
 'Does the contract cap the supplier''s total aggregate liability, state the amount or formula of the cap, and exclude fraud, wilful misconduct, data breaches or indemnified claims from the cap?',
 'Compliant: an explicit aggregate cap (for example the fees paid or payable in the preceding 12 months, or a fixed amount) together with carve-outs for fraud, wilful misconduct, gross negligence and confidentiality or data protection breaches. Partial: a cap exists but has no carve-outs. Non-compliant: unlimited liability for the customer, or a cap that also limits the supplier''s liability for fraud or data breach to a nominal amount.',
 'CRITICAL', TRUE, TRUE, now(), now()),

('INDEMNIFICATION',
 'Indemnification',
 'Does the supplier indemnify the customer against third-party claims arising from intellectual property infringement, breach of law, or the supplier''s negligence, and is the defence and notice procedure defined?',
 'Compliant: the supplier defends and indemnifies the customer against third-party IP infringement claims and claims caused by its negligence or breach of law, with a notice and control-of-defence procedure. Partial: indemnity limited to IP claims only or no procedure. Non-compliant: no supplier indemnity, or only the customer indemnifies the supplier.',
 'HIGH', TRUE, TRUE, now(), now()),

('CONFIDENTIALITY',
 'Confidentiality',
 'Does the contract oblige the parties to keep confidential information secret, limit its use to the purpose of the agreement, and continue the obligation for a defined period after termination?',
 'Compliant: mutual confidentiality obligations, use limited to performing the agreement, standard exclusions (public information, independently developed, legally required disclosure) and survival for at least 3 years after termination. Partial: obligation exists but survival period is missing or shorter than 3 years. Non-compliant: no confidentiality clause, or the supplier may disclose customer information freely.',
 'HIGH', TRUE, TRUE, now(), now()),

('GOVERNING_LAW',
 'Governing law and jurisdiction',
 'Does the contract specify the governing law and the courts or arbitration forum that will resolve disputes?',
 'Compliant: a single named governing law and an exclusive forum (named courts or an arbitration seat and rules). Partial: governing law stated but no forum, or forum stated without governing law. Non-compliant: no governing-law clause, or the supplier may choose the forum unilaterally.',
 'MEDIUM', TRUE, TRUE, now(), now()),

('IP_OWNERSHIP',
 'Intellectual property ownership',
 'Who owns the intellectual property in deliverables created for the customer under the agreement, and does the customer receive at least a perpetual licence to any supplier background IP needed to use them?',
 'Compliant: the customer owns deliverables created specifically for it (assignment on creation or payment) and receives a perpetual, royalty-free licence to supplier background IP embedded in the deliverables. Partial: the customer only receives a licence to the deliverables. Non-compliant: the supplier retains all rights and the customer''s use ends on termination.',
 'HIGH', TRUE, TRUE, now(), now()),

('DATA_PROTECTION',
 'Data protection and security',
 'Does the contract require the supplier to process personal data only on documented instructions, implement appropriate technical and organisational security measures, and notify the customer of a personal data breach within a defined time?',
 'Compliant: processing only on documented instructions, defined security measures, breach notification within 72 hours or less, sub-processor approval and deletion or return of data at the end of the agreement. Partial: some of these obligations are present but breach notification timing is missing. Non-compliant: no data protection clause although personal data is processed.',
 'CRITICAL', TRUE, TRUE, now(), now()),

('SLA_PENALTIES',
 'Service levels and service credits',
 'Does the contract define measurable service levels (for example availability or response times) and service credits or other remedies when they are missed?',
 'Compliant: measurable service levels with a measurement period, service credits for misses and a right to terminate for repeated failure. Partial: service levels defined but no remedy, or remedies capped below a meaningful level. Non-compliant: service levels described only as best efforts with no measurement.',
 'MEDIUM', FALSE, TRUE, now(), now()),

('FORCE_MAJEURE',
 'Force majeure',
 'Does the contract define force majeure events, require prompt notice and mitigation, and allow termination if the event continues for an extended period?',
 'Compliant: a defined list of events beyond reasonable control, a notice obligation, a duty to mitigate, and a termination right if the event lasts more than 30 to 90 days. Partial: events defined but no termination right or notice duty. Non-compliant: force majeure excuses the supplier''s payment-related or data-security obligations, or no clause exists.',
 'MEDIUM', FALSE, TRUE, now(), now()),

('AUTO_RENEWAL',
 'Automatic renewal',
 'Does the agreement renew automatically, and if so, how much advance notice is required to prevent renewal and are price increases on renewal limited?',
 'Compliant: no automatic renewal, or automatic renewal for at most 12 months with a non-renewal notice period of at most 90 days and capped price increases. Partial: automatic renewal with a reasonable notice period but no price cap. Non-compliant: multi-year automatic renewal, a non-renewal notice period over 90 days, or uncapped renewal price increases.',
 'LOW', FALSE, TRUE, now(), now()),

('AUDIT_RIGHTS',
 'Audit rights',
 'Does the customer have the right to audit the supplier''s records, controls and compliance with the agreement, with reasonable notice, and is the supplier required to cooperate?',
 'Compliant: the customer (or an independent auditor) may audit relevant records and security controls on reasonable notice, at least annually, and the supplier must cooperate and remediate findings. Partial: audit limited to financial records only or to supplier-provided reports. Non-compliant: no audit right.',
 'HIGH', TRUE, TRUE, now(), now());
