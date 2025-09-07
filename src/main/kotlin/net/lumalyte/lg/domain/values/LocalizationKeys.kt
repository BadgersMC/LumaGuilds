package net.lumalyte.lg.domain.values

object LocalizationKeys {
    // -------------------------------------
    // General Messages
    // -------------------------------------
    const val GENERAL_ERROR = "general.error"
    const val GENERAL_NAME_ERROR = "general.name_error"
    const val GENERAL_LIST_SEPARATOR = "general.list_separator"

    // -------------------------------------
    // Action Feedback
    // -------------------------------------

    // Claim
    const val FEEDBACK_CLAIM_DENIED = "feedback.claim.denied"
    const val FEEDBACK_CLAIM_OWNER = "feedback.claim.owner"

    // Destruction
    const val FEEDBACK_DESTRUCTION_ATTACHED = "feedback.destruction.attached"
    const val FEEDBACK_DESTRUCTION_PERMISSION = "feedback.destruction.permission"
    const val FEEDBACK_DESTRUCTION_PENDING = "feedback.destruction.pending"
    const val FEEDBACK_DESTRUCTION_SUCCESS = "feedback.destruction.success"

    // Edit Tool
    const val FEEDBACK_EDIT_TOOL_IN_CLAIM = "feedback.edit_tool.in_claim"
    const val FEEDBACK_EDIT_TOOL_INSUFFICIENT = "feedback.edit_tool.insufficient"
    const val FEEDBACK_EDIT_TOOL_INVALID = "feedback.edit_tool.invalid"
    const val FEEDBACK_EDIT_TOOL_MINIMUM_SIZE = "feedback.edit_tool.minimum_size"
    const val FEEDBACK_EDIT_TOOL_NEW_PARTITION = "feedback.edit_tool.new_partition"
    const val FEEDBACK_EDIT_TOOL_NOT_CONNECTED = "feedback.edit_tool.not_connected"
    const val FEEDBACK_EDIT_TOOL_OVERLAP = "feedback.edit_tool.overlap"
    const val FEEDBACK_EDIT_TOOL_PERMISSION = "feedback_edit_tool.permission"
    const val FEEDBACK_EDIT_TOOL_START_EXTENSION = "feedback.edit_tool.start_extension"
    const val FEEDBACK_EDIT_TOOL_START_RESIZE = "feedback_edit_tool.start_resize"
    const val FEEDBACK_EDIT_TOOL_SUCCESSFUL_RESIZE = "feedback_edit_tool.successful_resize"
    const val FEEDBACK_EDIT_TOOL_TOO_CLOSE = "feedback.edit_tool.too_close"
    const val FEEDBACK_EDIT_TOOL_UNEQUIP_BUILD = "feedback.edit_tool.unequip_build"
    const val FEEDBACK_EDIT_TOOL_UNEQUIP_RESIZE = "feedback.edit_tool.unequip_resize"
    const val FEEDBACK_EDIT_TOOL_UNSELECT_BUILD = "feedback.edit_tool.unselect_build"
    const val FEEDBACK_EDIT_TOOL_UNSELECT_RESIZE = "feedback.edit_tool.unselect_resize"

    // Move Tool
    const val FEEDBACK_MOVE_TOOL_SUCCESS = "feedback.move_tool.success"
    const val FEEDBACK_MOVE_TOOL_OUTSIDE_BORDER = "feedback.move_tool.outside_border"
    const val FEEDBACK_MOVE_TOOL_NO_PERMISSION = "feedback.move_tool.no_permission"

    // Transfer
    const val FEEDBACK_TRANSFER_SUCCESS = "feedback.transfer.success"


    // -------------------------------------
    // Conditions
    // -------------------------------------

    // Accept Transfer Conditions
    const val ACCEPT_TRANSFER_CONDITION_INVALID_REQUEST = "accept_transfer_condition.invalid_request"
    const val ACCEPT_TRANSFER_CONDITION_INVALID_CLAIM = "accept_transfer_condition.invalid_claim"
    const val ACCEPT_TRANSFER_CONDITION_OWNER = "accept_transfer_condition.owner"

    // Creation Conditions
    const val CREATION_CONDITION_BLOCKS = "creation_condition.blocks"
    const val CREATION_CONDITION_CLAIMS = "creation_condition.claims"
    const val CREATION_CONDITION_EXISTING = "creation_condition.existing"
    const val CREATION_CONDITION_OVERLAP = "creation_condition.overlap"
    const val CREATION_CONDITION_UNNAMED = "creation_condition.unnamed"
    const val CREATION_CONDITION_WORLD_BORDER = "creation_condition.world_border"

    // Transfer Conditions
    const val SEND_TRANSFER_CONDITION_BLOCKS = "send_transfer_condition.blocks"
    const val SEND_TRANSFER_CONDITION_CLAIMS = "send_transfer_condition.claims"
    const val SEND_TRANSFER_CONDITION_EXIST = "send_transfer_condition.exist"
    const val SEND_TRANSFER_CONDITION_OWNER = "send_transfer_condition.owner"


    // -------------------------------------
    // Permissions
    // -------------------------------------
    const val PERMISSION_BUILD_NAME = "permission.build.name"
    const val PERMISSION_BUILD_LORE = "permission.build.lore"
    const val PERMISSION_CONTAINER_NAME = "permission.container.name"
    const val PERMISSION_CONTAINER_LORE = "permission.container.lore"
    const val PERMISSION_DETONATE_NAME = "permission.detonate.name"
    const val PERMISSION_DETONATE_LORE = "permission.detonate.lore"
    const val PERMISSION_DISPLAY_NAME = "permission.display.name"
    const val PERMISSION_DISPLAY_LORE = "permission.display.lore"
    const val PERMISSION_DOOR_NAME = "permission.door.name"
    const val PERMISSION_DOOR_LORE = "permission.door.lore"
    const val PERMISSION_EVENT_NAME = "permission.event.name"
    const val PERMISSION_EVENT_LORE = "permission.event.lore"
    const val PERMISSION_HARVEST_NAME = "permission.harvest.name"
    const val PERMISSION_HARVEST_LORE = "permission.harvest.lore"
    const val PERMISSION_HUSBANDRY_NAME = "permission.husbandry.name"
    const val PERMISSION_HUSBANDRY_LORE = "permission.husbandry.lore"
    const val PERMISSION_VEHICLE_NAME = "permission.vehicle.name"
    const val PERMISSION_VEHICLE_LORE = "permission.vehicle.lore"
    const val PERMISSION_REDSTONE_NAME = "permission.redstone.name"
    const val PERMISSION_REDSTONE_LORE = "permission.redstone.lore"
    const val PERMISSION_SLEEP_NAME = "permission.sleep.name"
    const val PERMISSION_SLEEP_LORE = "permission.sleep.lore"
    const val PERMISSION_SIGN_NAME = "permission.sign.name"
    const val PERMISSION_SIGN_LORE = "permission.sign.lore"
    const val PERMISSION_TRADE_NAME = "permission.trade.name"
    const val PERMISSION_TRADE_LORE = "permission.trade.lore"
    const val PERMISSION_VIEW_NAME = "permission.view.name"
    const val PERMISSION_VIEW_LORE = "permission.view.lore"


    // -------------------------------------
    // Flags
    // -------------------------------------
    const val FLAG_DISPENSER_NAME = "flag.dispenser.name"
    const val FLAG_DISPENSER_LORE = "flag.dispenser.lore"
    const val FLAG_EXPLOSION_NAME = "flag.explosion.name"
    const val FLAG_EXPLOSION_LORE = "flag.explosion.lore"
    const val FLAG_FALLING_BLOCK_NAME = "flag.falling_block.name"
    const val FLAG_FALLING_BLOCK_LORE = "flag.falling_block.lore"
    const val FLAG_FIRE_NAME = "flag.fire.name"
    const val FLAG_FIRE_LORE = "flag.fire.lore"
    const val FLAG_FLUID_NAME = "flag.fluid.name"
    const val FLAG_FLUID_LORE = "flag.fluid.lore"
    const val FLAG_LIGHTNING_NAME = "flag.lightning.name"
    const val FLAG_LIGHTNING_LORE = "flag.lightning.lore"
    const val FLAG_MOB_NAME = "flag.mob.name"
    const val FLAG_MOB_LORE = "flag.mob.lore"
    const val FLAG_PASSIVE_ENTITY_VEHICLE_NAME = "flag.passive_entity_vehicle.name"
    const val FLAG_PASSIVE_ENTITY_VEHICLE_LORE = "flag.passive_entity_vehicle.lore"
    const val FLAG_PISTON_NAME = "flag.piston.name"
    const val FLAG_PISTON_LORE = "flag.piston.lore"
    const val FLAG_TREE_NAME = "flag.tree.name"
    const val FLAG_TREE_LORE = "flag.tree.lore"
    const val FLAG_SCULK_NAME = "flag.sculk.name"
    const val FLAG_SCULK_LORE = "flag.sculk.lore"
    const val FLAG_SPONGE_NAME = "flag.sponge.name"
    const val FLAG_SPONGE_LORE = "flag.sponge.lore"


    // -------------------------------------
    // Menu Elements
    // -------------------------------------

    // Common Menu Items
    const val MENU_COMMON_ITEM_BACK_NAME = "menu.common.item.back.name"
    const val MENU_COMMON_ITEM_CLOSE_NAME = "menu.common.item.close.name"
    const val MENU_COMMON_ITEM_CONFIRM_NAME = "menu.common.item.confirm.name"
    const val MENU_COMMON_ITEM_DESELECT_ALL_NAME = "menu.common.item.deselect_all.name"
    const val MENU_COMMON_ITEM_ERROR_NAME = "menu.common.item.error.name"
    const val MENU_COMMON_ITEM_ERROR_LORE = "menu.common.item.error.lore"
    const val MENU_COMMON_ITEM_NEXT_NAME = "menu.common.item.next.name"
    const val MENU_COMMON_ITEM_PAGE_NAME = "menu.common.item.page.name"
    const val MENU_COMMON_ITEM_PREV_NAME = "menu.common.item.prev.name"
    const val MENU_COMMON_ITEM_SELECT_ALL_NAME = "menu.common.item.select_all.name"

    // All Players Menu
    const val MENU_ALL_PLAYERS_TITLE = "menu.all_players.title"
    const val MENU_ALL_PLAYERS_ITEM_SEARCH_NAME = "menu.all_players.item.search.name"
    const val MENU_ALL_PLAYERS_ITEM_SEARCH_LORE = "menu.all_players.item.search.lore"

    // Claim List
    const val MENU_CLAIM_LIST_TITLE = "menu.claim_list.title"

    // Claim Wide Permissions Menu
    const val MENU_CLAIM_WIDE_PERMISSIONS_TITLE = "menu.claim_wide_permissions.title"
    const val MENU_CLAIM_WIDE_PERMISSIONS_ITEM_INFO_NAME = "menu.claim_wide_permissions.item.info.name"

    // Confirm Partition Delete
    const val MENU_CONFIRM_PARTITION_DELETE_TITLE = "menu.confirm_partition_delete.title"

    // Confirmation Menu
    const val MENU_CONFIRMATION_ITEM_NO_NAME = "menu.confirmation.item.no.name"
    const val MENU_CONFIRMATION_ITEM_NO_LORE = "menu.confirmation.item.no.lore"
    const val MENU_CONFIRMATION_ITEM_YES_NAME = "menu.confirmation.item.yes.name"
    const val MENU_CONFIRMATION_ITEM_YES_LORE = "menu.confirmation.item.yes.lore"

    // Creation Menu
    const val MENU_CREATION_TITLE = "menu.creation.title"
    const val MENU_CREATION_ITEM_CANNOT_CREATE_NAME = "menu.creation.item.cannot_create.name"
    const val MENU_CREATION_ITEM_CREATE_NAME = "menu.creation.item.create.name"
    const val MENU_CREATION_ITEM_CREATE_LORE_PROTECTED = "menu.creation.item.create.lore.protected"
    const val MENU_CREATION_ITEM_CREATE_LORE_REMAINING = "menu.creation.item.create.lore.remaining"

    // Edit Tool Menu
    const val MENU_EDIT_TOOL_TITLE = "menu.edit_tool.title"
    const val MENU_EDIT_TOOL_ITEM_CHANGE_MODE_NAME = "menu.edit_tool.item.change_mode.name"
    const val MENU_EDIT_TOOL_ITEM_CHANGE_MODE_LORE_VIEW = "menu.edit_tool.item.change_mode.lore.view"
    const val MENU_EDIT_TOOL_ITEM_CHANGE_MODE_LORE_EDIT = "menu.edit_tool.item.change_mode.lore.edit"
    const val MENU_EDIT_TOOL_ITEM_CHANGE_MODE_LORE_VIEW_ACTIVE = "menu.edit_tool.item.change_mode.lore.view_active"
    const val MENU_EDIT_TOOL_ITEM_CHANGE_MODE_LORE_EDIT_ACTIVE = "menu.edit_tool.item.change_mode.lore.edit_active"
    const val MENU_EDIT_TOOL_ITEM_NO_CLAIM_NAME = "menu.edit_tool.item.no_claim.name"
    const val MENU_EDIT_TOOL_ITEM_NO_CLAIM_LORE = "menu.edit_tool.item.no_claim.lore"
    const val MENU_EDIT_TOOL_ITEM_NO_PERMISSION_NAME = "menu.edit_tool.item.no_permission.name"
    const val MENU_EDIT_TOOL_ITEM_NO_PERMISSION_LORE = "menu.edit_tool.item.no_permission.lore"
    const val MENU_EDIT_TOOL_ITEM_CLAIM_NAME = "menu.edit_tool.item.claim.name"
    const val MENU_EDIT_TOOL_ITEM_CLAIM_LORE_CLAIM_NAME = "menu.edit_tool.item.claim.lore.claim_name"
    const val MENU_EDIT_TOOL_ITEM_CLAIM_LORE_LOCATION = "menu.edit_tool.item.claim.lore.location"
    const val MENU_EDIT_TOOL_ITEM_CLAIM_LORE_PARTITIONS = "menu.edit_tool.item.claim.lore.partitions"
    const val MENU_EDIT_TOOL_ITEM_CLAIM_LORE_BLOCKS = "menu.edit_tool.item.claim.lore.blocks"
    const val MENU_EDIT_TOOL_ITEM_PARTITION_NAME = "menu.edit_tool.item.partition.name"
    const val MENU_EDIT_TOOL_ITEM_PARTITION_LORE_LOCATION = "menu.edit_tool.item.partition.lore.location"
    const val MENU_EDIT_TOOL_ITEM_PARTITION_LORE_BLOCKS = "menu.edit_tool.item.partition.lore.blocks"
    const val MENU_EDIT_TOOL_ITEM_DELETE_NAME = "menu.edit_tool.item.delete.name"
    const val MENU_EDIT_TOOL_ITEM_CANNOT_DELETE_NAME = "menu.edit_tool.item.cannot_delete.name"
    const val MENU_EDIT_TOOL_ITEM_CANNOT_DELETE_LORE = "menu.edit_tool.item.cannot_delete.lore"

    // Flags Menu
    const val MENU_FLAGS_TITLE = "menu.flags.title"

    // Icon Menu items
    const val MENU_ICON_TITLE = "menu.icon.title"
    const val MENU_ICON_ITEM_INFO_NAME = "menu.icon.item.info.name"
    const val MENU_ICON_ITEM_INFO_LORE = "menu.icon.item.info.lore"

    // Management Menu
    const val MENU_MANAGEMENT_TITLE = "menu.management.title"
    const val MENU_MANAGEMENT_ITEM_FLAGS_NAME = "menu.management.item.flags.name"
    const val MENU_MANAGEMENT_ITEM_ICON_NAME = "menu.management.item.icon.name"
    const val MENU_MANAGEMENT_ITEM_ICON_LORE = "menu.management.item.icon.lore"
    const val MENU_MANAGEMENT_ITEM_MOVE_NAME = "menu.management.item.move.name"
    const val MENU_MANAGEMENT_ITEM_MOVE_LORE = "menu.management.item.move.lore"
    const val MENU_MANAGEMENT_ITEM_CONVERT_GUILD_NAME = "menu.management.item.convert_guild.name"
    const val MENU_MANAGEMENT_ITEM_CONVERT_GUILD_LORE = "menu.management.item.convert_guild.lore"
    const val MENU_MANAGEMENT_ITEM_PERMISSIONS_NAME = "menu.management.item.permissions.name"
    const val MENU_MANAGEMENT_ITEM_RENAME_NAME = "menu.management.item.rename.name"
    const val MENU_MANAGEMENT_ITEM_RENAME_LORE = "menu.management.item.rename.lore"
    const val MENU_MANAGEMENT_ITEM_TOOL_NAME = "menu.management.item.tool.name"
    const val MENU_MANAGEMENT_ITEM_TOOL_LORE = "menu.management.item.tool.lore"

    // Naming Menu
    const val MENU_NAMING_TITLE = "menu.naming.title"
    const val MENU_NAMING_ITEM_CANNOT_CREATE_NAME = "menu.naming.item.cannot_create.name"

    // Player Permissions Menu
    const val MENU_PLAYER_PERMISSIONS_TITLE = "menu.player_permissions.title"
    const val MENU_PLAYER_PERMISSIONS_ITEM_CANCEL_TRANSFER_NAME = "menu.player_permissions.item.cancel_transfer.name"
    const val MENU_PLAYER_PERMISSIONS_ITEM_CANCEL_TRANSFER_LORE = "menu.player_permissions.item.cancel_transfer.lore"
    const val MENU_PLAYER_PERMISSIONS_ITEM_CANNOT_TRANSFER_NAME = "menu.player_permissions.item.cannot_transfer.name"
    const val MENU_PLAYER_PERMISSIONS_ITEM_TRANSFER_NAME = "menu.player_permissions.item.transfer.name"
    const val MENU_PLAYER_PERMISSIONS_ITEM_TRANSFER_LORE = "menu.player_permissions.item.transfer.lore"

    // Player Search Menu
    const val MENU_PLAYER_SEARCH_TITLE = "menu.player_search.title"
    const val MENU_PLAYER_SEARCH_ITEM_PLAYER_NAME = "menu.player_search.item.player.name"
    const val MENU_PLAYER_SEARCH_ITEM_PLAYER_UNKNOWN_NAME = "menu.player_search.item.player_unknown.name"
    const val MENU_PLAYER_SEARCH_ITEM_PLAYER_UNKNOWN_LORE = "menu.player_search.item.player_unknown.lore"

    // Renaming Menu
    const val MENU_RENAMING_TITLE = "menu.renaming.title"
    const val MENU_RENAMING_ITEM_EXISTING_NAME = "menu.renaming.item.existing.name"
    const val MENU_RENAMING_ITEM_UNKNOWN_NAME = "menu.renaming.item.unknown.name"

    // Transfer Menu
    const val MENU_TRANSFER_TITLE = "menu.transfer.title"

    // Transfer Send Menu
    const val MENU_TRANSFER_SEND_TITLE = "menu.transfer_send.title"

    // Trusted Players Menu
    const val MENU_TRUSTED_PLAYERS_TITLE = "menu.trusted_players.title"
    const val MENU_TRUSTED_PLAYERS_ITEM_ALL_PLAYERS_NAME = "menu.trusted_players.item.all_players.name"
    const val MENU_TRUSTED_PLAYERS_ITEM_ALL_PLAYERS_LORE = "menu.trusted_players.item.all_players.lore"
    const val MENU_TRUSTED_PLAYERS_ITEM_DEFAULT_PERMISSIONS_NAME = "menu.trusted_players.item.default_permissions.name"
    const val MENU_TRUSTED_PLAYERS_ITEM_DEFAULT_PERMISSIONS_LORE = "menu.trusted_players.item.default_permissions.lore"
    const val MENU_TRUSTED_PLAYERS_ITEM_HAS_PERMISSION_LORE = "menu.trusted_players.item.has_permission.lore"


    // -------------------------------------
    // Commands
    // -------------------------------------

    // Common
    const val COMMAND_COMMON_INVALID_PAGE = "command.common.invalid_page"
    const val COMMAND_COMMON_UNKNOWN_CLAIM = "command.common.unknown_claim"
    const val COMMAND_COMMON_UNKNOWN_PARTITION = "command.common.unknown_partition"
    const val COMMAND_COMMON_NO_CLAIM_PERMISSION = "command.common.no_claim_permission"

    // Info Box
    const val COMMAND_INFO_BOX_INDEX = "command.info_box.index"
    const val COMMAND_INFO_BOX_PAGED = "command.info_box.paged"

    // Add Flag
    const val COMMAND_CLAIM_ADD_FLAG_SUCCESS = "command.claim.add_flag.success"
    const val COMMAND_CLAIM_ADD_FLAG_ALREADY_EXISTS = "command.claim.add_flag.already_exists"

    // Claim
    const val COMMAND_CLAIM_SUCCESS = "command.claim.success"
    const val COMMAND_CLAIM_ALREADY_HAVE_TOOL = "command.claim.already_have_tool"

    // Claim List
    const val COMMAND_CLAIM_LIST_NO_CLAIMS = "command.claim_list.no_claims"
    const val COMMAND_CLAIM_LIST_HEADER = "command.claim_list.header"
    const val COMMAND_CLAIM_LIST_ROW = "command.claim_list.row"

    // Claim Override
    const val COMMAND_CLAIM_OVERRIDE_ENABLED = "command.claim_override.enabled"
    const val COMMAND_CLAIM_OVERRIDE_DISABLED = "command.claim_override.disabled"

    // Claim Description
    const val COMMAND_CLAIM_DESCRIPTION_SUCCESS = "command.claim.description.success"
    const val COMMAND_CLAIM_DESCRIPTION_EXCEED_LIMIT = "command.claim.description.exceed_limit"
    const val COMMAND_CLAIM_DESCRIPTION_INVALID_CHARACTER = "command.claim.description.invalid_character"
    const val COMMAND_CLAIM_DESCRIPTION_BLACKLISTED_WORD = "command.claim.description.blacklisted_word"
    const val COMMAND_CLAIM_DESCRIPTION_BLANK = "command.claim.description.blank"

    // Claim Info
    const val COMMAND_CLAIM_INFO_HEADER = "command.claim.info.header"
    const val COMMAND_CLAIM_INFO_ROW_OWNER = "command.claim.info.row.owner"
    const val COMMAND_CLAIM_INFO_ROW_CREATION_DATE = "command.claim.info.row.creation_date"
    const val COMMAND_CLAIM_INFO_ROW_PARTITION_COUNT = "command.claim.info.row.partition_count"
    const val COMMAND_CLAIM_INFO_ROW_BLOCK_COUNT = "command.claim.info.row.block_count"
    const val COMMAND_CLAIM_INFO_ROW_FLAGS = "command.claim.info.row.flags"
    const val COMMAND_CLAIM_INFO_ROW_DEFAULT_PERMISSIONS = "command.claim.info.row.default_permissions"
    const val COMMAND_CLAIM_INFO_ROW_TRUSTED_USERS = "command.claim.info.row.trusted_users"

    // Claim Partitions
    const val COMMAND_CLAIM_PARTITIONS_HEADER = "command.partitions.header"
    const val COMMAND_CLAIM_PARTITIONS_ROW = "command.partitions.row"

    // Claim Remove
    const val COMMAND_CLAIM_REMOVE_SUCCESS = "command.claim.remove.success"
    const val COMMAND_CLAIM_REMOVE_UNKNOWN_PARTITION = "command.claim.remove.unknown_partition"
    const val COMMAND_CLAIM_REMOVE_DISCONNECTED = "command.claim.remove.disconnected"
    const val COMMAND_CLAIM_REMOVE_EXPOSED_ANCHOR = "command.claim.remove.exposed_anchor"

    // Claim Remove Flag
    const val COMMAND_CLAIM_REMOVE_FLAG_SUCCESS = "command.claim.remove_flag.success"
    const val COMMAND_CLAIM_REMOVE_FLAG_DOES_NOT_EXIST = "command.claim.remove_flag.does_not_exist"

    // Claim Rename
    const val COMMAND_CLAIM_RENAME_SUCCESS = "command.claim.rename.success"
    const val COMMAND_CLAIM_RENAME_ALREADY_EXISTS = "command.claim.rename.already_exists"
    const val COMMAND_CLAIM_RENAME_EXCEED_LIMIT = "command.claim.rename.exceed_limit"
    const val COMMAND_CLAIM_RENAME_INVALID_CHARACTER = "command.claim.rename.invalid_character"
    const val COMMAND_CLAIM_RENAME_BLACKLISTED_WORD = "command.claim.rename.blacklisted_word"
    const val COMMAND_CLAIM_RENAME_BLANK = "command.claim.rename.blank"

    // Claim Trust All
    const val COMMAND_CLAIM_TRUST_ALL_SUCCESS = "command.claim.trust_all.success"
    const val COMMAND_CLAIM_TRUST_ALL_ALREADY_EXISTS = "command.claim.trust_all.already_exists"

    // Claim Trust
    const val COMMAND_CLAIM_TRUST_SUCCESS = "command.claim.trust.success"
    const val COMMAND_CLAIM_TRUST_ALREADY_EXISTS = "command.claim.trust.already_exists"

    // Claim Trust List
    const val COMMAND_CLAIM_TRUST_LIST_NO_PLAYERS = "command.claim.trust_list.no_players"
    const val COMMAND_CLAIM_TRUST_LIST_HEADER = "command.claim.trust_list.header"
    const val COMMAND_CLAIM_TRUST_LIST_ROW = "command.claim.trust_list.row"

    // Claim Untrust All
    const val COMMAND_CLAIM_UNTRUST_ALL_SUCCESS = "command.claim.untrust_all.success"
    const val COMMAND_CLAIM_UNTRUST_ALL_DOES_NOT_EXIST = "command.claim.untrust_all.does_not_exist"

    // Claim Untrust
    const val COMMAND_CLAIM_UNTRUST_SUCCESS = "command.claim.untrust.success"
    const val COMMAND_CLAIM_UNTRUST_DOES_NOT_EXIST = "command.claim.untrust.does_not_exist"

    // =====================================
    // Guild System Keys
    // =====================================
    
    // Guild General
    const val GUILD_CREATED = "guild.created"
    const val GUILD_DISBANDED = "guild.disbanded"
    const val GUILD_JOINED = "guild.joined"
    const val GUILD_LEFT = "guild.left"
    const val GUILD_NOT_FOUND = "guild.not_found"
    const val GUILD_ALREADY_EXISTS = "guild.already_exists"
    const val GUILD_INVALID_NAME = "guild.invalid_name"
    const val GUILD_NO_PERMISSION = "guild.no_permission"
    const val GUILD_NOT_MEMBER = "guild.not_member"
    const val GUILD_ALREADY_MEMBER = "guild.already_member"
    const val GUILD_MAX_MEMBERS_REACHED = "guild.max_members_reached"
    
    // Guild Modes
    const val GUILD_MODE_PEACEFUL = "guild.mode.peaceful"
    const val GUILD_MODE_HOSTILE = "guild.mode.hostile"
    const val GUILD_MODE_CHANGED = "guild.mode.changed"
    const val GUILD_MODE_COOLDOWN = "guild.mode.cooldown"
    const val GUILD_MODE_BLOCKED_BY_WAR = "guild.mode.blocked_by_war"
    
    // Guild Home System
    const val GUILD_HOME_SET = "guild.home.set"
    const val GUILD_HOME_TELEPORTING = "guild.home.teleporting"
    const val GUILD_HOME_TELEPORTED = "guild.home.teleported"
    const val GUILD_HOME_NOT_SET = "guild.home.not_set"
    const val GUILD_HOME_COOLDOWN = "guild.home.cooldown"
    const val GUILD_HOME_UNSAFE = "guild.home.unsafe"
    
    // Guild Ranks
    const val GUILD_RANK_CREATED = "guild.rank.created"
    const val GUILD_RANK_DELETED = "guild.rank.deleted"
    const val GUILD_RANK_RENAMED = "guild.rank.renamed"
    const val GUILD_RANK_PROMOTED = "guild.rank.promoted"
    const val GUILD_RANK_DEMOTED = "guild.rank.demoted"
    const val GUILD_RANK_NOT_FOUND = "guild.rank.not_found"
    const val GUILD_RANK_INVALID_NAME = "guild.rank.invalid_name"
    const val GUILD_RANK_MAX_REACHED = "guild.rank.max_reached"
    
    // Guild Relations
    const val GUILD_RELATION_ALLY_REQUEST_SENT = "guild.relation.ally.request_sent"
    const val GUILD_RELATION_ALLY_REQUEST_RECEIVED = "guild.relation.ally.request_received"
    const val GUILD_RELATION_ALLY_ACCEPTED = "guild.relation.ally.accepted"
    const val GUILD_RELATION_ALLY_REJECTED = "guild.relation.ally.rejected"
    const val GUILD_RELATION_ENEMY_SET = "guild.relation.enemy.set"
    const val GUILD_RELATION_TRUCE_REQUEST_SENT = "guild.relation.truce.request_sent"
    const val GUILD_RELATION_TRUCE_ACCEPTED = "guild.relation.truce.accepted"
    const val GUILD_RELATION_NEUTRAL_SET = "guild.relation.neutral.set"
    
    // Guild Bank
    const val GUILD_BANK_DEPOSITED = "guild.bank.deposited"
    const val GUILD_BANK_WITHDRAWN = "guild.bank.withdrawn"
    const val GUILD_BANK_INSUFFICIENT_FUNDS = "guild.bank.insufficient_funds"
    const val GUILD_BANK_INSUFFICIENT_PERSONAL_FUNDS = "guild.bank.insufficient_personal_funds"
    const val GUILD_BANK_BALANCE = "guild.bank.balance"
    const val GUILD_BANK_TRANSACTION_LIMIT = "guild.bank.transaction_limit"
    const val GUILD_BANK_DAILY_LIMIT = "guild.bank.daily_limit"
    
    // Guild Wars
    const val GUILD_WAR_DECLARED = "guild.war.declared"
    const val GUILD_WAR_RECEIVED = "guild.war.received"
    const val GUILD_WAR_ENDED = "guild.war.ended"
    const val GUILD_WAR_VICTORY = "guild.war.victory"
    const val GUILD_WAR_DEFEAT = "guild.war.defeat"
    const val GUILD_WAR_MAX_WARS = "guild.war.max_wars"
    const val GUILD_WAR_COOLDOWN = "guild.war.cooldown"
    
    // Guild Chat
    const val GUILD_CHAT_CHANNEL_ENABLED = "guild.chat.channel_enabled"
    const val GUILD_CHAT_CHANNEL_DISABLED = "guild.chat.channel_disabled"
    const val GUILD_CHAT_ALLY_ENABLED = "guild.chat.ally_enabled"
    const val GUILD_CHAT_ALLY_DISABLED = "guild.chat.ally_disabled"
    const val GUILD_CHAT_ANNOUNCED = "guild.chat.announced"
    const val GUILD_CHAT_PING_ALL = "guild.chat.ping_all"
    const val GUILD_CHAT_COOLDOWN = "guild.chat.cooldown"
    const val GUILD_CHAT_EMOJI_NOT_ALLOWED = "guild.chat.emoji_not_allowed"
    
    // Guild Progression
    const val GUILD_PROGRESSION_LEVEL_UP = "guild.progression.level_up"
    const val GUILD_PROGRESSION_PERK_UNLOCKED = "guild.progression.perk_unlocked"
    const val GUILD_PROGRESSION_EXPERIENCE_GAINED = "guild.progression.experience_gained"
    
    // Guild Menu Elements
    const val MENU_GUILD_TITLE = "menu.guild.title"
    const val MENU_GUILD_ITEM_INFO_NAME = "menu.guild.item.info.name"
    const val MENU_GUILD_ITEM_INFO_LORE_NAME = "menu.guild.item.info.lore.name"
    const val MENU_GUILD_ITEM_INFO_LORE_LEVEL = "menu.guild.item.info.lore.level"
    const val MENU_GUILD_ITEM_INFO_LORE_MEMBERS = "menu.guild.item.info.lore.members"
    const val MENU_GUILD_ITEM_INFO_LORE_BALANCE = "menu.guild.item.info.lore.balance"
    const val MENU_GUILD_ITEM_INFO_LORE_MODE = "menu.guild.item.info.lore.mode"
    const val MENU_GUILD_ITEM_MEMBERS_NAME = "menu.guild.item.members.name"
    const val MENU_GUILD_ITEM_MEMBERS_LORE = "menu.guild.item.members.lore"
    const val MENU_GUILD_ITEM_BANK_NAME = "menu.guild.item.bank.name"
    const val MENU_GUILD_ITEM_BANK_LORE = "menu.guild.item.bank.lore"
    const val MENU_GUILD_ITEM_RELATIONS_NAME = "menu.guild.item.relations.name"
    const val MENU_GUILD_ITEM_RELATIONS_LORE = "menu.guild.item.relations.lore"
    const val MENU_GUILD_ITEM_WARS_NAME = "menu.guild.item.wars.name"
    const val MENU_GUILD_ITEM_WARS_LORE = "menu.guild.item.wars.lore"
    const val MENU_GUILD_ITEM_HOME_NAME = "menu.guild.item.home.name"
    const val MENU_GUILD_ITEM_HOME_LORE = "menu.guild.item.home.lore"
    const val MENU_GUILD_ITEM_SETTINGS_NAME = "menu.guild.item.settings.name"
    const val MENU_GUILD_ITEM_SETTINGS_LORE = "menu.guild.item.settings.lore"
    

    
    // Guild Commands
    const val COMMAND_GUILD_CREATE_SUCCESS = "command.guild.create.success"
    const val COMMAND_GUILD_CREATE_USAGE = "command.guild.create.usage"
    const val COMMAND_GUILD_DISBAND_SUCCESS = "command.guild.disband.success"
    const val COMMAND_GUILD_DISBAND_CONFIRM = "command.guild.disband.confirm"
    const val COMMAND_GUILD_INVITE_SUCCESS = "command.guild.invite.success"
    const val COMMAND_GUILD_INVITE_USAGE = "command.guild.invite.usage"
    const val COMMAND_GUILD_KICK_SUCCESS = "command.guild.kick.success"
    const val COMMAND_GUILD_KICK_USAGE = "command.guild.kick.usage"
    const val COMMAND_GUILD_LEAVE_SUCCESS = "command.guild.leave.success"
    const val COMMAND_GUILD_INFO_HEADER = "command.guild.info.header"
    const val COMMAND_GUILD_INFO_LEVEL = "command.guild.info.level"
    const val COMMAND_GUILD_INFO_MEMBERS = "command.guild.info.members"
    const val COMMAND_GUILD_INFO_BANK = "command.guild.info.bank"
    const val COMMAND_GUILD_INFO_MODE = "command.guild.info.mode"
    const val COMMAND_GUILD_LIST_HEADER = "command.guild.list.header"
    const val COMMAND_GUILD_HOME_USAGE = "command.guild.home.usage"
    const val COMMAND_GUILD_SETHOME_SUCCESS = "command.guild.sethome.success"
    const val COMMAND_GUILD_SETHOME_USAGE = "command.guild.sethome.usage"
    
    // Error Messages
    const val ERROR_GUILD_NOT_OWNER = "error.guild.not_owner"
    const val ERROR_GUILD_NOT_ADMIN = "error.guild.not_admin"
    const val ERROR_GUILD_PLAYER_NOT_FOUND = "error.guild.player_not_found"
    const val ERROR_GUILD_PLAYER_NOT_MEMBER = "error.guild.player_not_member"
    const val ERROR_GUILD_CANNOT_TARGET_SELF = "error.guild.cannot_target_self"
    const val ERROR_GUILD_NAME_TOO_LONG = "error.guild.name_too_long"
    const val ERROR_GUILD_NAME_TOO_SHORT = "error.guild.name_too_short"
    const val ERROR_GUILD_INVALID_CHARACTERS = "error.guild.invalid_characters"
    const val ERROR_BANK_INVALID_AMOUNT = "error.bank.invalid_amount"
    const val ERROR_BANK_AMOUNT_TOO_SMALL = "error.bank.amount_too_small"
    const val ERROR_BANK_AMOUNT_TOO_LARGE = "error.bank.amount_too_large"
    const val ERROR_BANK_NEGATIVE_AMOUNT = "error.bank.negative_amount"
    const val ERROR_WAR_CANNOT_WAR_SELF = "error.war.cannot_war_self"
    const val ERROR_WAR_ALREADY_AT_WAR = "error.war.already_at_war"
    const val ERROR_WAR_PEACEFUL_MODE = "error.war.peaceful_mode"
    const val ERROR_WAR_TARGET_PEACEFUL = "error.war.target_peaceful"

    // -------------------------------------
    // Items
    // -------------------------------------

    // Claim Tool
    const val ITEM_CLAIM_TOOL_NAME = "item.claim_tool.name"
    const val ITEM_CLAIM_TOOL_LORE_MAIN_HAND = "item.claim_tool.lore.main_hand"
    const val ITEM_CLAIM_TOOL_LORE_OFF_HAND = "item.claim_tool.lore.off_hand"

    // Move Tool
    const val ITEM_MOVE_TOOL_NAME = "item.move_tool.name"
    const val ITEM_MOVE_TOOL_LORE = "item.move_tool.lore"

    // -------------------------------------
    // Guild Bank Menu
    // -------------------------------------

    // Menu Titles
    const val MENU_BANK_TITLE = "menu.bank.title"

    // Balance Display
    const val MENU_BANK_BALANCE_TITLE = "menu.bank.balance.title"
    const val MENU_BANK_BALANCE_CURRENT = "menu.bank.balance.current"
    const val MENU_BANK_BALANCE_NO_TRANSACTIONS = "menu.bank.balance.no_transactions"

    // Quick Actions
    const val MENU_BANK_QUICK_DEPOSIT_100 = "menu.bank.quick.deposit.100"
    const val MENU_BANK_QUICK_DEPOSIT_1000 = "menu.bank.quick.deposit.1000"
    const val MENU_BANK_QUICK_DEPOSIT_10000 = "menu.bank.quick.deposit.10000"
    const val MENU_BANK_QUICK_DEPOSIT_ALL = "menu.bank.quick.deposit.all"
    const val MENU_BANK_QUICK_WITHDRAW_100 = "menu.bank.quick.withdraw.100"
    const val MENU_BANK_QUICK_WITHDRAW_1000 = "menu.bank.quick.withdraw.1000"
    const val MENU_BANK_QUICK_WITHDRAW_10000 = "menu.bank.quick.withdraw.10000"
    const val MENU_BANK_QUICK_WITHDRAW_ALL = "menu.bank.quick.withdraw.all"

    // Custom Amount Actions
    const val MENU_BANK_CUSTOM_DEPOSIT = "menu.bank.custom.deposit"
    const val MENU_BANK_CUSTOM_WITHDRAW = "menu.bank.custom.withdraw"

    // Transaction History
    const val MENU_BANK_HISTORY_TITLE = "menu.bank.history.title"
    const val MENU_BANK_HISTORY_NO_TRANSACTIONS = "menu.bank.history.no_transactions"
    const val MENU_BANK_HISTORY_TRANSACTION_FORMAT = "menu.bank.history.transaction_format"
    const val MENU_BANK_HISTORY_VIEW_MORE = "menu.bank.history.view_more"

    // Statistics
    const val MENU_BANK_STATS_TITLE = "menu.bank.stats.title"
    const val MENU_BANK_STATS_TOTAL_DEPOSITS = "menu.bank.stats.total_deposits"
    const val MENU_BANK_STATS_TOTAL_WITHDRAWALS = "menu.bank.stats.total_withdrawals"
    const val MENU_BANK_STATS_TRANSACTION_COUNT = "menu.bank.stats.transaction_count"
    const val MENU_BANK_STATS_VOLUME = "menu.bank.stats.volume"

    // Feedback Messages
    const val MENU_BANK_FEEDBACK_DEPOSIT_SUCCESS = "menu.bank.feedback.deposit_success"
    const val MENU_BANK_FEEDBACK_WITHDRAW_SUCCESS = "menu.bank.feedback.withdraw_success"
    const val MENU_BANK_FEEDBACK_INSUFFICIENT_PLAYER_FUNDS = "menu.bank.feedback.insufficient_player_funds"
    const val MENU_BANK_FEEDBACK_INSUFFICIENT_GUILD_FUNDS = "menu.bank.feedback.insufficient_guild_funds"
    const val MENU_BANK_FEEDBACK_INVALID_AMOUNT = "menu.bank.feedback.invalid_amount"
    const val MENU_BANK_FEEDBACK_NO_PERMISSION = "menu.bank.feedback.no_permission"
    const val MENU_BANK_FEEDBACK_AMOUNT_TOO_SMALL = "menu.bank.feedback.amount_too_small"
    const val MENU_BANK_FEEDBACK_AMOUNT_TOO_LARGE = "menu.bank.feedback.amount_too_large"

    // Transaction Types
    const val MENU_BANK_TRANSACTION_DEPOSIT = "menu.bank.transaction.deposit"
    const val MENU_BANK_TRANSACTION_WITHDRAWAL = "menu.bank.transaction.withdrawal"
    const val MENU_BANK_TRANSACTION_FEE = "menu.bank.transaction.fee"

    // Navigation
    const val MENU_BANK_BACK_TO_CONTROL_PANEL = "menu.bank.back_to_control_panel"
    const val MENU_BANK_CLOSE = "menu.bank.close"

    // Transaction History Menu
    const val MENU_BANK_HISTORY_FILTER_TYPE = "menu.bank.history.filter.type"
    const val MENU_BANK_HISTORY_FILTER_MEMBER = "menu.bank.history.filter.member"
    const val MENU_BANK_HISTORY_FILTER_DATE = "menu.bank.history.filter.date"
    const val MENU_BANK_HISTORY_FILTER_SEARCH = "menu.bank.history.filter.search"
    const val MENU_BANK_HISTORY_FILTER_CLEAR = "menu.bank.history.filter.clear"
    const val MENU_BANK_HISTORY_PAGE_PREVIOUS = "menu.bank.history.page.previous"
    const val MENU_BANK_HISTORY_PAGE_NEXT = "menu.bank.history.page.next"
    const val MENU_BANK_HISTORY_EXPORT = "menu.bank.history.export"
    const val MENU_BANK_HISTORY_EXPORT_SUCCESS = "menu.bank.history.export.success"
    const val MENU_BANK_HISTORY_EXPORT_FAILED = "menu.bank.history.export.failed"

    // Statistics Menu
    const val MENU_BANK_STATS_ACTIVITY_VERY_HIGH = "menu.bank.stats.activity.very_high"
    const val MENU_BANK_STATS_ACTIVITY_HIGH = "menu.bank.stats.activity.high"
    const val MENU_BANK_STATS_ACTIVITY_MODERATE = "menu.bank.stats.activity.moderate"
    const val MENU_BANK_STATS_ACTIVITY_LOW = "menu.bank.stats.activity.low"
    const val MENU_BANK_STATS_ACTIVITY_NONE = "menu.bank.stats.activity.none"
    const val MENU_BANK_STATS_TREND_WEEKLY = "menu.bank.stats.trend.weekly"
    const val MENU_BANK_STATS_TREND_MONTHLY = "menu.bank.stats.trend.monthly"
    const val MENU_BANK_STATS_TREND_PEAK = "menu.bank.stats.trend.peak"
    const val MENU_BANK_STATS_CONTRIBUTOR_TOP = "menu.bank.stats.contributor.top"
    const val MENU_BANK_STATS_ACTIVITY_RECENT = "menu.bank.stats.activity.recent"
    const val MENU_BANK_STATS_BUDGET_STATUS = "menu.bank.stats.budget.status"
    const val MENU_BANK_STATS_MEMBER_SUMMARY = "menu.bank.stats.member.summary"
    const val MENU_BANK_STATS_TAX_COLLECTION = "menu.bank.stats.tax.collection"
    const val MENU_BANK_STATS_REFRESH_DATA = "menu.bank.stats.refresh.data"

    // Budget Menu
    const val MENU_BANK_BUDGET_TITLE = "menu.bank.budget.title"
    const val MENU_BANK_BUDGET_MONTHLY = "menu.bank.budget.monthly"
    const val MENU_BANK_BUDGET_WEEKLY = "menu.bank.budget.weekly"
    const val MENU_BANK_BUDGET_DAILY = "menu.bank.budget.daily"
    const val MENU_BANK_BUDGET_STATUS = "menu.bank.budget.status"
    const val MENU_BANK_BUDGET_ALERTS = "menu.bank.budget.alerts"
    const val MENU_BANK_BUDGET_NO_ALERTS = "menu.bank.budget.no_alerts"
    const val MENU_BANK_BUDGET_SAVE = "menu.bank.budget.save"
    const val MENU_BANK_BUDGET_SPENT = "menu.bank.budget.spent"
    const val MENU_BANK_BUDGET_REMAINING = "menu.bank.budget.remaining"
    const val MENU_BANK_BUDGET_PERCENT = "menu.bank.budget.percent"

    // Security Menu
    const val MENU_BANK_SECURITY_TITLE = "menu.bank.security.title"
    const val MENU_BANK_SECURITY_DUAL_AUTH = "menu.bank.security.dual_auth"
    const val MENU_BANK_SECURITY_FRAUD_DETECTION = "menu.bank.security.fraud_detection"
    const val MENU_BANK_SECURITY_EMERGENCY_FREEZE = "menu.bank.security.emergency_freeze"
    const val MENU_BANK_SECURITY_RISK_LEVEL = "menu.bank.security.risk_level"
    const val MENU_BANK_SECURITY_EVENTS = "menu.bank.security.events"
    const val MENU_BANK_SECURITY_ALERTS = "menu.bank.security.alerts"
    const val MENU_BANK_SECURITY_NO_ALERTS = "menu.bank.security.no_alerts"
    const val MENU_BANK_SECURITY_AUDIT_LOG = "menu.bank.security.audit_log"
    const val MENU_BANK_SECURITY_SAVE_SETTINGS = "menu.bank.security.save_settings"
    const val MENU_BANK_SECURITY_RISK_LOW = "menu.bank.security.risk.low"
    const val MENU_BANK_SECURITY_RISK_MEDIUM = "menu.bank.security.risk.medium"
    const val MENU_BANK_SECURITY_RISK_HIGH = "menu.bank.security.risk.high"
    const val MENU_BANK_SECURITY_RISK_CRITICAL = "menu.bank.security.risk.critical"

    // Automation Menu
    const val MENU_BANK_AUTOMATION_TITLE = "menu.bank.automation.title"
    const val MENU_BANK_AUTOMATION_SCHEDULED_DEPOSITS = "menu.bank.automation.scheduled_deposits"
    const val MENU_BANK_AUTOMATION_AUTO_REWARDS = "menu.bank.automation.auto_rewards"
    const val MENU_BANK_AUTOMATION_BUDGET_ALERTS = "menu.bank.automation.budget_alerts"
    const val MENU_BANK_AUTOMATION_RECURRING_PAYMENTS = "menu.bank.automation.recurring_payments"
    const val MENU_BANK_AUTOMATION_INTEREST_RATE = "menu.bank.automation.interest_rate"
    const val MENU_BANK_AUTOMATION_ACTIVE_COUNT = "menu.bank.automation.active_count"
    const val MENU_BANK_AUTOMATION_NEXT_RUN = "menu.bank.automation.next_run"
    const val MENU_BANK_AUTOMATION_STATUS = "menu.bank.automation.status"
    const val MENU_BANK_AUTOMATION_RECENT_ACTIVITY = "menu.bank.automation.recent_activity"
    const val MENU_BANK_AUTOMATION_REWARD_SETUP = "menu.bank.automation.reward_setup"
    const val MENU_BANK_AUTOMATION_ALERT_CONFIG = "menu.bank.automation.alert_config"
    const val MENU_BANK_AUTOMATION_PAYMENT_SETUP = "menu.bank.automation.payment_setup"
    const val MENU_BANK_AUTOMATION_SAVE_SETTINGS = "menu.bank.automation.save_settings"
}
