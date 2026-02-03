# MiniML Change Proposals

This directory contains numbered proposals for significant changes to the MiniML language, compiler, or standard library.

## Purpose

Change proposals provide:
- Documentation of design decisions
- Discussion space for new features
- Historical record of language evolution
- Clear specification before implementation

## When to Write a Proposal

Write a proposal for:
- New language syntax or semantics
- Type system changes
- Breaking changes to existing features
- Major compiler architecture changes
- Standard library additions

Not needed for:
- Bug fixes
- Internal refactoring (unless it affects public API)
- Documentation improvements
- Test additions

## Proposal Format

Each proposal is a markdown file named `NNN-short-title.md` where:
- `NNN` is a zero-padded sequential number (001, 002, etc.)
- `short-title` is a brief kebab-case description

### Required Sections

1. **Title**: One-line summary
2. **Status**: Current state (see below)
3. **Author**: Who proposed it
4. **Date**: When proposed
5. **Problem**: What issue does this solve?
6. **Solution**: High-level approach
7. **Detailed Design**: Technical specification
8. **Test Plan**: How to verify correctness
9. **Benefits**: Why implement this?
10. **Drawbacks**: Costs and concerns (optional)
11. **Alternatives**: Other approaches considered (optional)
12. **Future Work**: Follow-on improvements (optional)

### Writing Focus

When writing proposals, emphasize:
- **User-facing syntax**: What will users actually write?
- **Semantics**: How does the feature behave? What are the rules?
- **Benefits**: Why is this valuable? What problems does it solve?
- **Drawbacks**: What are the costs, limitations, and potential issues?

Keep implementation details minimal unless they affect user-observable behavior or inform design tradeoffs.

## Proposal Statuses

- **Proposed**: Under discussion, not yet approved
- **Accepted**: Approved for implementation
- **Implemented**: Code complete, tests passing
- **Rejected**: Decided not to pursue
- **Withdrawn**: Author withdrew proposal
- **Superseded**: Replaced by another proposal

## Process

1. Create proposal file with `Proposed` status
2. Discuss and iterate on the design
3. Update status to `Accepted` when ready to implement
4. Implement the changes
5. Update status to `Implemented` when complete
6. Update GRAMMAR.md, DESIGN.md, CLAUDE.md as needed

## Index

| Number | Title | Status |
|--------|-------|--------|
| 001 | [Java Foreign Types](001-java-foreign-types.md) | Implemented |
| 002 | [Polymorphic Type Syntax](002-polymorphic-type-syntax.md) | Proposed |
