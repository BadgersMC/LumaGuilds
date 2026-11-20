# Unique guild advancement titles

When Guild Mastery is displayed, the End and first guild-war advancements shall use distinct titles while retaining their existing IDs, requirements, rewards, and progress.

The prior title `Free the End` matches Minecraft 26.2's End advancement. `To the Victor Go the Spoils` also appears in the EnthusiaTags Warzone advancement source; its current production registration has not been verified.

The JSON definitions change only the two display titles. A local structural comparison against the parent commit confirmed all 30 node IDs and every non-title field are unchanged. The new titles do not match a title in the local Minecraft 26.2 English language data or appear in the inspected EnthusiaTags and EnthusiaAdvancements source trees. This is not a complete live custom-advancement catalog or native-client verification.

This is a display-label change: no behavioral engine change is needed. Hosted checks, production deployment, and in-game acceptance remain separate. No project EARS or state-helper tooling was found for this change; this record captures the requirement and evidence.
