package ru.iopump.qa.allure.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit coverage for {@link HumanSize#format(long)} — all six branches (negative, B, KB, MB, GB, TB)
 * plus the boundary values where the unit tier flips. The formatter pins {@link java.util.Locale#ROOT}
 * so the decimal separator is always {@code '.'} regardless of the host locale.
 */
class HumanSizeTest {

    private static final long KB = 1024L;
    private static final long MB = KB * 1024L;
    private static final long GB = MB * 1024L;
    private static final long TB = GB * 1024L;

    @ParameterizedTest(name = "{0} bytes -> \"{1}\"")
    @DisplayName("should format byte counts as IEC strings across every unit tier and boundary")
    @CsvSource({
        "-1, '0 B'",
        "-1048576, '0 B'",
        "0, '0 B'",
        "1, '1 B'",
        "1023, '1023 B'",
        "1024, '1.0 KB'",
        "1536, '1.5 KB'",
        "1048575, '1024.0 KB'",
        "1048576, '1.0 MB'",
        "1572864, '1.5 MB'",
        "1073741823, '1024.0 MB'",
        "1073741824, '1.0 GB'",
        "1610612736, '1.5 GB'",
        "1099511627775, '1024.0 GB'",
        "1099511627776, '1.0 TB'",
        "1649267441664, '1.5 TB'"
    })
    void format_coversAllTiersAndBoundaries(long bytes, String expected) {
        // GIVEN — a byte count straddling a unit boundary (see @CsvSource)

        // WHEN — formatting it
        final String actual = HumanSize.format(bytes);

        // THEN — the rendered string matches the expected IEC text exactly
        assertThat(actual)
            .as("HumanSize.format(%d) must render exactly '%s', with '.' as the decimal separator "
                + "(Locale.ROOT), never a locale-specific comma", bytes, expected)
            .isEqualTo(expected)
            .doesNotContain(",");
    }
}
