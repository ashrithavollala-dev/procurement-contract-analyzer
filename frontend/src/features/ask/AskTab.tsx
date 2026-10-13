import { useMutation } from '@tanstack/react-query';
import { AlertTriangle, Bot, FileText, Send, User } from 'lucide-react';
import { useEffect, useRef, useState, type FormEvent } from 'react';
import { askContract } from '../../api/ask';
import { ApiError } from '../../api/errors';
import { Card, Spinner } from '../../components/Feedback';
import { buttonClasses, inputClasses } from '../../components/styles';
import { explainFailure } from '../../notifications/errorMessages';
import type { AskResponse, Contract } from '../../types/api';
import { formatDuration } from '../../types/labels';

interface Exchange {
  id: number;
  question: string;
  response?: AskResponse;
  error?: ApiError;
}

const EXAMPLES = [
  'How much notice is needed to terminate for convenience?',
  'Is the supplier liability capped?',
  'Which law governs the agreement?',
];

export function AskTab({ contract }: { contract: Contract }) {
  const [exchanges, setExchanges] = useState<Exchange[]>([]);
  const [question, setQuestion] = useState('');
  const nextId = useRef(1);
  const ready = contract.status === 'READY';
  const ask = useMutation({ mutationFn: (q: string) => askContract(contract.id, { question: q }) });
  const elapsed = useTicker(ask.isPending);

  const submit = (event?: FormEvent, preset?: string) => {
    event?.preventDefault();
    const text = (preset ?? question).trim();
    if (text.length < 3 || ask.isPending) return;
    const id = nextId.current++;
    setExchanges((current) => [...current, { id, question: text }]);
    setQuestion('');
    ask.mutate(text, {
      onSuccess: (response) =>
        setExchanges((current) => current.map((e) => (e.id === id ? { ...e, response } : e))),
      onError: (error) =>
        setExchanges((current) =>
          current.map((e) =>
            e.id === id ? { ...e, error: error instanceof ApiError ? error : new ApiError(0, 'INTERNAL_ERROR', 'Failed') } : e,
          ),
        ),
    });
  };

  return (
    <Card title="Ask about this contract">
      {!ready && <p className="mb-3 text-sm text-ink-muted">Questions can be asked once the contract is READY.</p>}
      <div className="mb-4 max-h-[28rem] space-y-4 overflow-y-auto" aria-live="polite">
        {exchanges.length === 0 && (
          <div className="text-sm text-ink-muted">
            <p className="mb-2">Answers come only from retrieved contract passages, with page citations. Try:</p>
            <div className="flex flex-wrap gap-2">
              {EXAMPLES.map((example) => (
                <button
                  key={example}
                  type="button"
                  disabled={!ready || ask.isPending}
                  className="rounded-full border border-surface-border px-3 py-1 text-xs hover:bg-surface-muted disabled:opacity-50"
                  onClick={() => submit(undefined, example)}
                >
                  {example}
                </button>
              ))}
            </div>
          </div>
        )}
        {exchanges.map((exchange) => (
          <div key={exchange.id} className="space-y-2">
            <div className="flex items-start gap-2">
              <User className="mt-1 h-4 w-4 text-ink-faint" aria-hidden />
              <p className="rounded-card bg-brand-50 px-3 py-2 text-sm text-ink">{exchange.question}</p>
            </div>
            <div className="flex items-start gap-2">
              <Bot className="mt-1 h-4 w-4 text-brand-500" aria-hidden />
              <div className="flex-1">
                {exchange.response ? (
                  <AnswerView response={exchange.response} />
                ) : exchange.error ? (
                  <p role="alert" className="rounded-card bg-status-dangerBg px-3 py-2 text-sm text-status-danger">
                    {explainFailure(exchange.error.code)}
                  </p>
                ) : (
                  <Spinner label={`Thinking... ${formatDuration(elapsed)} (local models can take a few minutes)`} />
                )}
              </div>
            </div>
          </div>
        ))}
      </div>
      <form onSubmit={submit} className="flex gap-2">
        <input
          className={inputClasses}
          placeholder="Ask a question about this contract"
          value={question}
          maxLength={1000}
          disabled={!ready}
          onChange={(event) => setQuestion(event.target.value)}
          aria-label="Question"
        />
        <button type="submit" className={buttonClasses.primary} disabled={!ready || ask.isPending || question.trim().length < 3}>
          <Send className="h-4 w-4" aria-hidden /> Ask
        </button>
      </form>
    </Card>
  );
}

function AnswerView({ response }: { response: AskResponse }) {
  return (
    <div className="space-y-2">
      {!response.grounded && (
        <p role="alert" className="flex items-center gap-2 rounded-md bg-status-warnBg px-3 py-2 text-sm text-status-warn">
          <AlertTriangle className="h-4 w-4 shrink-0" aria-hidden />
          Not grounded: no relevant passage was found in this contract, so no answer can be given.
        </p>
      )}
      <p className="whitespace-pre-wrap rounded-card border border-surface-border bg-surface px-3 py-2 text-sm text-ink">
        {response.answer}
      </p>
      {response.citations.length > 0 && (
        <div className="flex flex-wrap gap-2" aria-label="Citations">
          {response.citations.map((citation) => (
            <span
              key={`${citation.page}-${citation.chunkIndex}`}
              title={citation.snippet}
              className="inline-flex max-w-xs items-center gap-1 rounded-full bg-surface-sunken px-2 py-0.5 text-xs text-ink-muted"
            >
              <FileText className="h-3 w-3 shrink-0" aria-hidden />
              <span className="font-medium">p.{citation.page}</span>
              <span className="truncate">{citation.snippet}</span>
            </span>
          ))}
        </div>
      )}
      <p className="text-xs text-ink-faint">Answered in {response.mode} mode</p>
    </div>
  );
}

function useTicker(running: boolean): number {
  const [seconds, setSeconds] = useState(0);
  useEffect(() => {
    if (!running) return undefined;
    setSeconds(0);
    const timer = window.setInterval(() => setSeconds((s) => s + 1), 1000);
    return () => window.clearInterval(timer);
  }, [running]);
  return seconds;
}
