package dev.abdelrahman.rankcorex.utils;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class MessageUtils {

    // Color code pattern for validation
    private static final Pattern COLOR_PATTERN = Pattern.compile("(?i)&[0-9A-FK-OR]");

    // Hex color pattern for newer versions
    private static final Pattern HEX_PATTERN = Pattern.compile("(?i)&#[0-9A-F]{6}");

    /**
     * Enhanced color translation with hex support
     */
    public static String colorize(String message) {
        if (message == null || message.isEmpty()) return "";

        // First handle hex colors (for newer versions)
        message = translateHexColors(message);

        // Then handle standard color codes
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    /**
     * Translate hex color codes (&#RRGGBB) to Minecraft format
     */
    private static String translateHexColors(String message) {
        // This is a placeholder for hex color support
        // Implementation depends on server version
        return message;
    }

    /**
     * Strip all color codes from message including hex
     */
    public static String stripColor(String message) {
        if (message == null || message.isEmpty()) return "";

        // Strip hex colors first
        message = HEX_PATTERN.matcher(message).replaceAll("");

        // Strip standard colors
        return ChatColor.stripColor(colorize(message));
    }

    /**
     * Get clean text length (without color codes)
     */
    public static int getCleanLength(String message) {
        return stripColor(message).length();
    }

    /**
     * Validate if string contains valid color codes
     */
    public static boolean hasValidColors(String message) {
        if (message == null) return true;
        return COLOR_PATTERN.matcher(message).find() || HEX_PATTERN.matcher(message).find();
    }

    // Enhanced Error Messages
    public static final String NO_PERMISSION = "&cYou do not have permission to use this command.";
    public static final String PLAYER_NOT_FOUND = "&cPlayer &f{player} &cnot found or never joined the server.";
    public static final String PLAYER_OFFLINE = "&cPlayer &f{player} &cis currently offline.";
    public static final String RANK_NOT_EXISTS = "&cRank &f{rank} &cdoes not exist.";
    public static final String NO_RANKS_DEFINED = "&cNo ranks are defined in ranks.yml!";
    public static final String INVALID_TIME_FORMAT = "&cInvalid time format! Use: s, m, h, d, mo, y (example: 7d)";
    public static final String CONFIG_RELOADED = "&aConfiguration reloaded successfully!";
    public static final String STORAGE_ERROR = "&cA storage error occurred. Please check the console for details.";
    public static final String SAME_RANK = "&cPlayer &f{player} &calready has rank &f{rank}&c.";
    public static final String COMMAND_COOLDOWN = "&cPlease wait &f{time} &cbefore using this command again.";
    public static final String INSUFFICIENT_ARGS = "&cInsufficient arguments. Use &f{usage}";
    public static final String TOO_MANY_ARGS = "&cToo many arguments. Use &f{usage}";

    // Enhanced Success Messages
    public static final String RANK_SET_PERMANENT = "&aSuccessfully set &f{player}&a's rank to &f{rank} &apermanently.";
    public static final String RANK_SET_TEMPORARY = "&aSuccessfully set &f{player}&a's rank to &f{rank} &afor &f{time}&a.";
    public static final String RANK_REMOVED = "&aSuccessfully removed rank &f{rank} &afrom player &f{player}&a.";
    public static final String RANK_EXPIRED = "&eYour rank &f{rank} &ehas expired and been removed.";
    public static final String RANK_UPDATED = "&aYour rank has been updated to &f{rank}&a.";

    // Enhanced Info Messages
    public static final String RANK_INFO_HEADER = "&6&l=== &f{player}&6&l's Rank Information &6&l===";
    public static final String RANK_INFO_CURRENT = "&eRank: &f{rank}";
    public static final String RANK_INFO_PREFIX = "&ePrefix: {prefix}";
    public static final String RANK_INFO_SUFFIX = "&eSuffix: {suffix}";
    public static final String RANK_INFO_WEIGHT = "&eWeight: &f{weight}";
    public static final String RANK_INFO_EXPIRES = "&eExpires: &f{expiry}";
    public static final String RANK_INFO_GIVEN = "&eGiven: &f{given}";
    public static final String RANK_INFO_BY = "&eGiven by: &f{giver}";
    public static final String RANK_INFO_REMAINING = "&eTime remaining: &f{remaining}";
    public static final String RANK_INFO_PERMANENT = "&aPermanent";
    public static final String RANK_INFO_EXPIRED = "&cExpired";

    public static final String RANK_LIST_HEADER = "&6&l=== Available Ranks &6&l===";
    public static final String RANK_LIST_ITEM = "&e{rank} &7(Weight: &f{weight}&7) &7- {prefix}";
    public static final String RANK_LIST_EMPTY = "&cNo ranks are available.";
    public static final String RANK_LIST_FOOTER = "&7Total ranks: &f{count}";

    // Enhanced Command Usage
    public static final String USAGE_MAIN = "&6&lRankCorex Commands:\n" +
            "&e/rank set <player> <rank> [time] &7- Set player's rank\n" +
            "&e/rank remove <player> &7- Remove player's rank\n" +
            "&e/rank check [player] &7- Check player's rank\n" +
            "&e/rank list &7- List all available ranks\n" +
            "&e/rank reload &7- Reload plugin configuration\n" +
            "&e/rank help &7- Show detailed help";

    public static final String USAGE_SET = "&cUsage: &f/rank set <player> <rank> [time]";
    public static final String USAGE_REMOVE = "&cUsage: &f/rank remove <player>";
    public static final String USAGE_CHECK = "&cUsage: &f/rank check [player]";
    public static final String USAGE_LIST = "&cUsage: &f/rank list [page]";

    // Help Messages
    public static final String HELP_HEADER = "&6&l=== RankCorex Help &6&l===";
    public static final String HELP_SET = "&e/rank set &f<player> <rank> [time]\n" +
            "&7  Set a player's rank permanently or temporarily\n" +
            "&7  Time examples: 30s, 5m, 1h, 7d, 30d, 1y";
    public static final String HELP_REMOVE = "&e/rank remove &f<player>\n" +
            "&7  Remove a player's current rank (sets to default)";
    public static final String HELP_CHECK = "&e/rank check &f[player]\n" +
            "&7  Check your own or another player's rank information";
    public static final String HELP_LIST = "&e/rank list &f[page]\n" +
            "&7  List all available ranks with their properties";
    public static final String HELP_RELOAD = "&e/rank reload\n" +
            "&7  Reload the plugin configuration and ranks";

    // Time-related Messages
    public static final String TIME_REMAINING_DAYS = "&f{days} &7days, &f{hours} &7hours";
    public static final String TIME_REMAINING_HOURS = "&f{hours} &7hours, &f{minutes} &7minutes";
    public static final String TIME_REMAINING_MINUTES = "&f{minutes} &7minutes";
    public static final String TIME_REMAINING_SECONDS = "&f{seconds} &7seconds";

    // Debug/Admin Messages
    public static final String DEBUG_MODE_ENABLED = "&aDebug mode enabled.";
    public static final String DEBUG_MODE_DISABLED = "&cDebug mode disabled.";
    public static final String CACHE_CLEARED = "&aPlayer rank cache cleared.";
    public static final String SYNC_STATUS = "&eSync status: &f{status}";

    /**
     * Enhanced placeholder replacement with validation
     */
    public static String replacePlaceholders(String message, String... replacements) {
        if (message == null) return "";

        String result = message;

        // Validate replacement pairs
        if (replacements.length % 2 != 0) {
            throw new IllegalArgumentException("Replacements must be in key-value pairs");
        }

        // Apply replacements
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length && replacements[i] != null && replacements[i + 1] != null) {
                result = result.replace("{" + replacements[i] + "}", replacements[i + 1]);
            }
        }

        return colorize(result);
    }

    /**
     * Replace placeholders using a map
     */
    public static String replacePlaceholders(String message, Map<String, String> placeholders) {
        if (message == null || placeholders == null) return colorize(message);

        String result = message;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                result = result.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }

        return colorize(result);
    }

    /**
     * Create a formatted message with multiple placeholders
     */
    public static String formatMessage(String template, Object... args) {
        if (template == null) return "";

        String result = template;
        for (int i = 0; i < args.length; i++) {
            result = result.replace("{" + i + "}", String.valueOf(args[i]));
        }

        return colorize(result);
    }

    /**
     * Send message with sound effect (if player)
     */
    public static void sendMessageWithSound(CommandSender sender, String message) {
        sender.sendMessage(colorize(message));

        if (sender instanceof Player) {
            Player player = (Player) sender;
            // Play a subtle notification sound
            try {
                player.playSound(player.getLocation(), "ui.button.click", 0.5f, 1.0f);
            } catch (Exception e) {
                // Ignore if sound doesn't exist on this version
            }
        }
    }

    /**
     * Send error message with error sound
     */
    public static void sendErrorMessage(CommandSender sender, String message) {
        sender.sendMessage(colorize(message));

        if (sender instanceof Player) {
            Player player = (Player) sender;
            try {
                player.playSound(player.getLocation(), "block.note_block.bass", 0.5f, 0.5f);
            } catch (Exception e) {
                // Ignore if sound doesn't exist on this version
            }
        }
    }

    /**
     * Send success message with success sound
     */
    public static void sendSuccessMessage(CommandSender sender, String message) {
        sender.sendMessage(colorize(message));

        if (sender instanceof Player) {
            Player player = (Player) sender;
            try {
                player.playSound(player.getLocation(), "entity.experience_orb.pickup", 0.5f, 1.2f);
            } catch (Exception e) {
                // Ignore if sound doesn't exist on this version
            }
        }
    }

    /**
     * Create a centered message
     */
    public static String centerMessage(String message) {
        if (message == null) return "";

        String cleanMessage = stripColor(message);
        int length = cleanMessage.length();

        if (length >= 80) return colorize(message);

        int spaces = (80 - length) / 2;
        StringBuilder centered = new StringBuilder();

        for (int i = 0; i < spaces; i++) {
            centered.append(" ");
        }
        centered.append(message);

        return colorize(centered.toString());
    }

    /**
     * Create a divider line
     */
    public static String createDivider(char character, int length) {
        StringBuilder divider = new StringBuilder("&7");
        for (int i = 0; i < length; i++) {
            divider.append(character);
        }
        return colorize(divider.toString());
    }

    /**
     * Format time duration in a human-readable way
     */
    public static String formatDuration(long seconds) {
        if (seconds <= 0) return "0 seconds";

        long days = seconds / (24 * 3600);
        seconds %= (24 * 3600);
        long hours = seconds / 3600;
        seconds %= 3600;
        long minutes = seconds / 60;
        seconds %= 60;

        StringBuilder result = new StringBuilder();

        if (days > 0) {
            result.append(days).append(" day").append(days > 1 ? "s" : "");
        }
        if (hours > 0) {
            if (result.length() > 0) result.append(", ");
            result.append(hours).append(" hour").append(hours > 1 ? "s" : "");
        }
        if (minutes > 0) {
            if (result.length() > 0) result.append(", ");
            result.append(minutes).append(" minute").append(minutes > 1 ? "s" : "");
        }
        if (seconds > 0 && result.length() == 0) {
            result.append(seconds).append(" second").append(seconds > 1 ? "s" : "");
        }

        return result.toString();
    }

    /**
     * Create a progress bar
     */
    public static String createProgressBar(double percentage, int length, char filled, char empty) {
        StringBuilder bar = new StringBuilder("&7[");
        int filledLength = (int) (length * (percentage / 100.0));

        bar.append("&a");
        for (int i = 0; i < filledLength; i++) {
            bar.append(filled);
        }

        bar.append("&7");
        for (int i = filledLength; i < length; i++) {
            bar.append(empty);
        }

        bar.append("&7] &f").append(String.format("%.1f", percentage)).append("%");

        return colorize(bar.toString());
    }

    /**
     * Validate message format and suggest corrections
     */
    public static String validateAndSuggest(String message) {
        if (message == null) return "Message cannot be null";
        if (message.isEmpty()) return "Message cannot be empty";

        // Check for common formatting issues
        StringBuilder suggestions = new StringBuilder();

        if (message.contains("&") && !hasValidColors(message)) {
            suggestions.append("Invalid color codes detected. ");
        }

        if (getCleanLength(message) > 100) {
            suggestions.append("Message is very long (").append(getCleanLength(message)).append(" chars). ");
        }

        return suggestions.length() > 0 ? suggestions.toString().trim() : "Valid";
    }

    /**
     * Safe message sending with error handling
     */
    public static void safeSendMessage(CommandSender sender, String message) {
        if (sender == null) return;

        try {
            sender.sendMessage(colorize(message));
        } catch (Exception e) {
            // Fallback to plain message
            try {
                sender.sendMessage(stripColor(message));
            } catch (Exception ex) {
                // Last resort - send error notification
                sender.sendMessage("Error displaying message. Check console for details.");
            }
        }
    }

    /**
     * Get appropriate article (aan) for a word
     */
    public static String getArticle(String word) {
        if (word == null || word.isEmpty()) return "a";

        char firstChar = Character.toLowerCase(word.charAt(0));
        return (firstChar == 'a' || firstChar == 'e' || firstChar == 'i' ||
                firstChar == 'o' || firstChar == 'u') ? "an" : "a";
    }

    /**
     * Pluralize a word based on count
     */
    public static String pluralize(String word, int count) {
        if (count == 1) return word;

        // Simple pluralization rules
        if (word.endsWith("y")) {
            return word.substring(0, word.length() - 1) + "ies";
        } else if (word.endsWith("s") || word.endsWith("sh") || word.endsWith("ch")) {
            return word + "es";
        } else {
            return word + "s";
        }
    }

    /**
     * Create a formatted list from collection
     */
    public static String formatList(java.util.List<String> items, String separator, String lastSeparator) {
        if (items == null || items.isEmpty()) return "";
        if (items.size() == 1) return items.get(0);
        if (items.size() == 2) return items.get(0) + " " + lastSeparator + " " + items.get(1);

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < items.size() - 1; i++) {
            result.append(items.get(i));
            if (i < items.size() - 2) {
                result.append(separator).append(" ");
            }
        }
        result.append(" ").append(lastSeparator).append(" ").append(items.get(items.size() - 1));

        return result.toString();
    }

    /**
     * Create a hover-text component (for compatible versions)
     */
    public static String createHoverText(String text, String hoverText) {
        // This would be implemented differently based on your text component system
        // For now, return the basic text
        return text;
    }

    /**
     * Truncate text with ellipsis
     */
    public static String truncate(String text, int maxLength) {
        if (text == null) return "";

        String clean = stripColor(text);
        if (clean.length() <= maxLength) return text;

        return text.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    /**
     * Constants for common characters and symbols
     */
    public static final class Symbols {
        public static final String ARROW_RIGHT = "→";
        public static final String ARROW_LEFT = "←";
        public static final String BULLET = "•";
        public static final String CHECK = "✓";
        public static final String CROSS = "✗";
        public static final String STAR = "★";
        public static final String HEART = "♥";
        public static final String DIAMOND = "♦";
        public static final String WARNING = "⚠";
        public static final String INFO = "ℹ";
        public static final String CROWN = "♔";

        private Symbols() {} // Utility class
    }

    /**
     * Common color combinations
     */
    public static final class Colors {
        public static final String PRIMARY = "&6";
        public static final String SECONDARY = "&e";
        public static final String SUCCESS = "&a";
        public static final String ERROR = "&c";
        public static final String WARNING = "&e";
        public static final String INFO = "&b";
        public static final String MUTED = "&7";
        public static final String ACCENT = "&f";
        public static final String HIGHLIGHT = "&l";

        private Colors() {} // Utility class
    }
}