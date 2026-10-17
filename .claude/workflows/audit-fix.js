export const meta = {
  name: 'audit-fix',
  description: 'Haiku audits files, validators drop false positives, Sonnet patches each fix in a worktree, verify cross-checks the diffs',
  whenToUse: 'Audit a set of files and fix what holds up. Pass args.batches: an array of file lists, one per audit agent.',
  phases: [
    { title: 'Audit', detail: 'one Haiku 5.5 scanner per batch of files', model: 'claude-haiku-5-5' },
    { title: 'Validate', detail: 'one Haiku 5.5 refuter per finding; refuted findings are dropped', model: 'claude-haiku-5-5' },
    { title: 'Patch', detail: 'one Sonnet 5.5 editor per finding, each in its own worktree', model: 'claude-sonnet-5-5' },
    { title: 'Verify', detail: 'independent check of each patch, then one cross-check of all diffs together' },
  ],
}

// Two rounds in a row with no progress ends a fix loop (CLAUDE.md).
const MAX_NO_PROGRESS = 2

const FINDINGS = {
  type: 'object',
  properties: {
    findings: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          file: { type: 'string' },
          line: { type: 'integer' },
          claim: { type: 'string' },
          evidence: { type: 'string' },
        },
        required: ['file', 'line', 'claim', 'evidence'],
      },
    },
  },
  required: ['findings'],
}
const VERDICT = {
  type: 'object',
  properties: { real: { type: 'boolean' }, reason: { type: 'string' } },
  required: ['real', 'reason'],
}
const PATCH = {
  type: 'object',
  properties: {
    changed: { type: 'boolean' },
    branch: { type: 'string' },
    files: { type: 'array', items: { type: 'string' } },
    buildPassed: { type: 'boolean' },
    summary: { type: 'string' },
  },
  required: ['changed', 'files', 'buildPassed', 'summary'],
}
const CROSS = {
  type: 'object',
  properties: {
    rejected: {
      type: 'array',
      items: {
        type: 'object',
        properties: { branch: { type: 'string' }, reason: { type: 'string' } },
        required: ['branch', 'reason'],
      },
    },
  },
  required: ['rejected'],
}

const describe = f => `${f.file}:${f.line}: ${f.claim}\nEvidence: ${f.evidence}`

const batches = (args && args.batches) || []
if (!batches.length) return { error: 'Pass args.batches: an array of file lists.' }

async function fixLoop(f, i) {
  let noProgress = 0
  let lastReason = ''
  while (noProgress < MAX_NO_PROGRESS) {
    const p = await agent(
      `Fix this finding with the smallest change that resolves it, build to check it, and commit on a new branch named claude/audit-fix-${i}.\n${describe(f)}` +
        (lastReason ? `\nThe last attempt was rejected: ${lastReason}` : ''),
      { agentType: 'editor', isolation: 'worktree', schema: PATCH, phase: 'Patch', label: `patch:${f.file}:${f.line}` })
    if (!p || !p.changed || !p.buildPassed) {
      noProgress++
      continue
    }
    const v = await agent(
      `Independently check this patch on branch ${p.branch}. Does it fix the finding without breaking anything else? Default to real=false if unsure.\nFinding: ${describe(f)}\nPatch summary: ${p.summary}`,
      { schema: VERDICT, phase: 'Verify', label: `verify:${f.file}:${f.line}` })
    if (v && v.real) return { finding: f, patch: p }
    const reason = v ? v.reason : 'verifier failed'
    if (reason === lastReason) noProgress++
    else noProgress = 1
    lastReason = reason
  }
  log(`Gave up on ${f.file}:${f.line} after ${MAX_NO_PROGRESS} rounds with no progress`)
  return null
}

const perBatch = await pipeline(
  batches,
  batch => agent(
    `Audit these files for real bugs. Report only what you can point to with file:line evidence.\n${batch.join('\n')}`,
    { agentType: 'scanner', schema: FINDINGS, phase: 'Audit', label: `audit:${batch[0]}` }),
  res => parallel(((res && res.findings) || []).map(f => () =>
    agent(
      `Try to refute this finding by reading the code. Default to real=false if you are unsure.\n${describe(f)}`,
      { agentType: 'scanner', schema: VERDICT, phase: 'Validate', effort: 'low', label: `validate:${f.file}:${f.line}` })
      .then(v => (v && v.real ? f : null)))),
  (kept, batch, b) => parallel(kept.filter(Boolean).map((f, j) => () => fixLoop(f, `${b}-${j}`))),
)

const fixes = perBatch.filter(Boolean).flat().filter(Boolean)
if (!fixes.length) return { fixes: [] }

// Barrier on purpose: this check needs every diff at once, to catch patches that conflict.
phase('Verify')
const cross = await agent(
  `Cross-check these branches together. Reject any that conflict with another or undo another's fix.\n` +
    fixes.map(x => `${x.patch.branch}: ${x.patch.summary} (${x.patch.files.join(', ')})`).join('\n'),
  { schema: CROSS, phase: 'Verify', label: 'cross-check' })
const rejected = new Set(((cross && cross.rejected) || []).map(r => r.branch))
for (const r of (cross && cross.rejected) || []) log(`Cross-check dropped ${r.branch}: ${r.reason}`)

// Only what survived verification goes back to the main session.
return { fixes: fixes.filter(x => !rejected.has(x.patch.branch)) }
