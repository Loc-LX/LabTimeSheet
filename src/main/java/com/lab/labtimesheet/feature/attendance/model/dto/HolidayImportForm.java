package com.lab.labtimesheet.feature.attendance.model.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Form backing the Admin holiday-import screen. Indexed selections align with the
 * rendered preview rows so the Admin decision posts back for the chosen year.
 */
@Getter
@Setter
public class HolidayImportForm {

    private int year;
    private List<HolidaySelectionForm> selections = new ArrayList<>();
}