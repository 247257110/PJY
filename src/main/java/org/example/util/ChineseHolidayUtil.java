package org.example.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 中国法定节假日工具类
 * 数据来源：国务院办公厅节假日安排通知（官方公告）
 * 包含：法定节假日（放假）+ 调休工作日（周末上班）
 * 覆盖年份：2023、2024、2025
 * 注意：每年需根据国务院最新通知更新数据
 */
public class ChineseHolidayUtil {

    /** 法定节假日（周末/工作日均放假） */
    private static final Set<LocalDate> HOLIDAYS = new HashSet<>();

    /** 调休工作日（周末需要上班） */
    private static final Set<LocalDate> WORKDAYS = new HashSet<>();

    static {
        // ===== 2023 =====
        // 元旦：1月1日(日)放假，1月2日(一)补休
        addHolidays("2023-01-01", "2023-01-02");
        addWorkdays("2022-12-31"); // 周六上班

        // 春节：1月21日-27日
        addHolidays("2023-01-21", "2023-01-22", "2023-01-23", "2023-01-24",
                "2023-01-25", "2023-01-26", "2023-01-27");
        addWorkdays("2023-01-28", "2023-01-29"); // 周六、周日上班

        // 清明节：4月3日-5日
        addHolidays("2023-04-03", "2023-04-04", "2023-04-05");
        addWorkdays("2023-04-23"); // 周日上班

        // 劳动节：4月29日-5月3日
        addHolidays("2023-04-29", "2023-04-30", "2023-05-01", "2023-05-02", "2023-05-03");
        addWorkdays("2023-05-06"); // 周六上班（4月23日已在清明调休中）

        // 端午节：6月22日-24日
        addHolidays("2023-06-22", "2023-06-23", "2023-06-24");
        addWorkdays("2023-06-25"); // 周日上班

        // 中秋节+国庆节：9月29日-10月6日
        addHolidays("2023-09-29", "2023-09-30",
                "2023-10-01", "2023-10-02", "2023-10-03", "2023-10-04", "2023-10-05", "2023-10-06");
        addWorkdays("2023-10-07", "2023-10-08"); // 周六、周日上班

        // ===== 2024 =====
        // 元旦：1月1日(一)
        addHolidays("2024-01-01");

        // 春节：2月10日-17日
        addHolidays("2024-02-10", "2024-02-11", "2024-02-12", "2024-02-13",
                "2024-02-14", "2024-02-15", "2024-02-16", "2024-02-17");
        addWorkdays("2024-02-04", "2024-02-18"); // 周日上班

        // 清明节：4月4日-6日
        addHolidays("2024-04-04", "2024-04-05", "2024-04-06");
        addWorkdays("2024-04-07"); // 周日上班

        // 劳动节：5月1日-5日
        addHolidays("2024-05-01", "2024-05-02", "2024-05-03", "2024-05-04", "2024-05-05");
        addWorkdays("2024-04-28", "2024-05-11"); // 周日、周六上班

        // 端午节：6月10日(一)
        addHolidays("2024-06-10");

        // 中秋节：9月15日-17日
        addHolidays("2024-09-15", "2024-09-16", "2024-09-17");
        addWorkdays("2024-09-14"); // 周六上班

        // 国庆节：10月1日-7日
        addHolidays("2024-10-01", "2024-10-02", "2024-10-03", "2024-10-04",
                "2024-10-05", "2024-10-06", "2024-10-07");
        addWorkdays("2024-09-29", "2024-10-12"); // 周日、周六上班

        // ===== 2025 =====
        // 元旦：1月1日(三)
        addHolidays("2025-01-01");

        // 春节：1月28日-2月4日
        addHolidays("2025-01-28", "2025-01-29", "2025-01-30", "2025-01-31",
                "2025-02-01", "2025-02-02", "2025-02-03", "2025-02-04");
        addWorkdays("2025-01-26", "2025-02-08"); // 周日、周六上班

        // 清明节：4月4日-6日
        addHolidays("2025-04-04", "2025-04-05", "2025-04-06");
        addWorkdays("2025-04-27"); // 周日上班（劳动节共用）

        // 劳动节：5月1日-5日
        addHolidays("2025-05-01", "2025-05-02", "2025-05-03", "2025-05-04", "2025-05-05");
        addWorkdays("2025-05-10"); // 周六上班（4月27日已在清明调休中）

        // 端午节：5月31日-6月2日
        addHolidays("2025-05-31", "2025-06-01", "2025-06-02");

        // 国庆节+中秋节：10月1日-8日
        addHolidays("2025-10-01", "2025-10-02", "2025-10-03", "2025-10-04",
                "2025-10-05", "2025-10-06", "2025-10-07", "2025-10-08");
        addWorkdays("2025-09-28", "2025-10-11"); // 周日、周六上班
    }

    private static void addHolidays(String... dates) {
        Arrays.stream(dates).map(LocalDate::parse).forEach(HOLIDAYS::add);
    }

    private static void addWorkdays(String... dates) {
        Arrays.stream(dates).map(LocalDate::parse).forEach(WORKDAYS::add);
    }

    /**
     * 判断某天是否为工作日（考虑法定节假日和调休）
     * 规则：
     *   1. 法定节假日 → 非工作日
     *   2. 调休工作日（周末上班）→ 工作日
     *   3. 周六/周日 → 非工作日
     *   4. 其余 → 工作日
     */
    public static boolean isWorkday(LocalDate date) {
        if (HOLIDAYS.contains(date)) return false;
        if (WORKDAYS.contains(date)) return true;
        DayOfWeek dow = date.getDayOfWeek();
        return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
    }

    /**
     * 判断某天是否为法定节假日（仅节假日，不含普通周末）
     */
    public static boolean isPublicHoliday(LocalDate date) {
        return HOLIDAYS.contains(date);
    }
}
