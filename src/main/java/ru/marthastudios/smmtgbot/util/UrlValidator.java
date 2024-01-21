package ru.marthastudios.smmtgbot.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UrlValidator {
    private static final String URL_REGEX = "^(?i)(ftp|http|https)?://[a-zA-Z0-9-]+(\\.[a-zA-Z]{2,})+(\\/[^\\s]*)?$";
    private static final Pattern URL_PATTERN = Pattern.compile(URL_REGEX);

    public static boolean isValid(String url) {
        Matcher matcher = URL_PATTERN.matcher(url);
        return matcher.matches();
    }
}
