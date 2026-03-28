# Report Issue Command Design

## Overview

Add a controller-only `/report-issue` slash command that opens a Discord modal, collects issue details from the user, and creates a GitHub issue in a configured repository when the modal is submitted. The success path ends with an ephemeral confirmation containing the created issue URL. Failures return an ephemeral error message and are logged server-side.

This feature requires extending the shared Discord abstraction to support modals because the current API only models slash command replies and deferred replies.

## Goals

- Give Discord users a guided way to report issues without leaving Discord
- Keep repository selection configurable per environment
- Keep controller code independent from JDA-specific modal APIs
- Include useful Discord metadata in the created GitHub issue body

## Non-Goals

- Button-based confirmation after modal submit
- Multi-repository routing logic beyond a single configured target per environment
- Full GitHub issue template parity
- Reuse outside the controller application

## User Flow

1. A user runs `/report-issue`
2. The command opens a modal titled `Report Issue`
3. The modal collects:
   - `title` (required)
   - `severity` (required short text)
   - `description` (required paragraph text)
   - `steps` (optional paragraph text)
4. When the user submits the modal, the bot validates the fields
5. If validation passes, the controller calls GitHub's issue creation API for the configured repository
6. The bot replies ephemerally with the created issue URL
7. If validation or issue creation fails, the bot replies ephemerally with a failure message

## Architecture

### Discord API Layer

Add modal abstractions to `discord/api` so controller code depends on generic interaction types rather than JDA events.

New concepts:

- `ModalDefinition`
  - Modal id
  - Title
  - Ordered list of text input definitions
- `ModalTextInputDefinition`
  - Field id
  - Label
  - Style (`SHORT` or `PARAGRAPH`)
  - Required flag
  - Minimum and maximum length where supported
  - Placeholder or default value if needed later
- `ModalSubmitInteraction`
  - `String getModalId()`
  - `Optional<String> getValue(String fieldId)`
  - `ReplyBuilder reply(String message)`
  - `EditableMessage defer(boolean ephemeral)` only if submission handlers need deferred responses
  - Access to existing metadata helpers such as user mention, guild id, and text channel id

`CommandInteraction` gains:

- `void showModal(ModalDefinition modal)`

The modal definition is a transport object. Validation for field presence and field meaning remains in controller code.

### Discord JDA Layer

Extend the JDA adapter to map the generic modal API to JDA primitives.

Required changes:

- Convert `ModalDefinition` to JDA `Modal` and text input components
- Add listener handling for `ModalInteractionEvent`
- Wrap `ModalInteractionEvent` in a new JDA-backed `ModalSubmitInteraction`
- Route modal submissions to handlers using a registry similar to command dispatch

The routing key should be the modal id, not the command name, because modal submission is a separate interaction type.

### Discord Spring Layer

Add a modal registry and annotation-based dispatch so modal submit handlers follow the same development model as slash commands.

Recommended model:

- Keep `@Command` and `@Interaction` for slash commands
- Add a dedicated annotation for modal submissions, for example `@ModalHandler("report-issue")`

This avoids overloading slash command routing with a second interaction type and keeps startup scanning explicit.

### Controller Layer

Add `ReportIssueCommand` under the controller Discord package.

Responsibilities:

- Slash command handler opens the modal
- Modal submit handler validates fields
- Maps modal values into a GitHub issue request
- Calls a GitHub reporting service
- Replies ephemerally with success or failure

Supporting types:

- `GitHubIssueReporter`
  - Small port/service responsible for creating GitHub issues
- `GitHubIssueProperties`
  - Holds configuration such as API token, owner, repo, and optional labels
- `GitHubIssueBodyFormatter`
  - Builds markdown body from modal data plus Discord metadata

## Data Flow

1. `/report-issue` slash command is dispatched
2. `ReportIssueCommand` constructs a modal definition and calls `showModal(...)`
3. User submits the modal
4. Modal registry dispatches the submission to the controller handler
5. Handler reads `title`, `severity`, `description`, and `steps`
6. Handler appends metadata:
   - reporter mention
   - guild id when present
   - text channel id when present
   - submission timestamp
7. `GitHubIssueReporter` sends `POST /repos/{owner}/{repo}/issues`
8. Success response returns the issue URL
9. Bot sends an ephemeral success message with the URL

## GitHub Integration

Implement a narrow REST client for GitHub issue creation only.

Configuration:

- `bearify.github.issue-reporter.enabled`
- `bearify.github.issue-reporter.owner`
- `bearify.github.issue-reporter.repo`
- `bearify.github.issue-reporter.token`
- `bearify.github.issue-reporter.labels` (optional)

Behavior:

- If the feature is disabled or misconfigured, the command should fail with an ephemeral message indicating that issue reporting is unavailable
- The token should be used only server-side and never echoed back to the user
- The created issue title should come from the modal title field
- The issue body should include structured sections for severity, description, steps, and Discord metadata
- Optional configured labels are attached to every created issue

## Validation

Validation happens in the controller submit handler even if Discord UI already enforces some limits.

Rules:

- `title` required, non-blank, bounded to a short length suitable for GitHub issue titles
- `severity` required, non-blank
- `description` required, non-blank
- `steps` optional

Failure behavior:

- Validation errors return an ephemeral message telling the user what field is invalid
- GitHub API failures return a generic ephemeral failure message
- Detailed API errors are logged for operators

## Error Handling

Cases to handle:

- Modal id not registered: generic ephemeral unsupported-interaction response
- Missing configuration: ephemeral unavailable message
- GitHub API returns non-success: ephemeral failure message, structured log entry
- Unexpected exception: use existing command advice path where practical, with an ephemeral generic error

## Testing

Add tests at the appropriate layers:

- `discord/jda`
  - modal definition to JDA modal conversion
  - modal submit event forwarding
- `discord/starter`
  - modal handler scanning and dispatch
- `discord/testing`
  - mock command interaction modal capture
  - mock modal submission interaction
- `controller`
  - command test verifies `/report-issue` opens the expected modal
  - submit handler test verifies validation behavior
  - submit handler test verifies GitHub payload mapping
  - integration test verifies success reply contains the created issue URL

## Security and Operational Notes

- GitHub tokens must come from environment-backed configuration, not source control
- Ephemeral responses prevent leaking issue details in public channels
- Discord metadata included in issues is intentional and should be treated as operator-visible information

## Recommended Implementation Order

1. Add modal abstractions in `discord/api`
2. Add modal support in `discord/jda`
3. Add registry and annotation support in `discord/starter`
4. Add mocks in `discord/testing`
5. Implement controller command and GitHub reporting service
6. Add integration and failure-path tests

## Out of Scope

- Dropdowns or richer severity selection controls
- Editing issues after creation
- Uploading Discord attachments to GitHub
- Buttons and confirmation prompts
