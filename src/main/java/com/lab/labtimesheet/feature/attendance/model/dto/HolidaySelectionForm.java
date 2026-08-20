package com.lab.labtimesheet.feature.attendance.model.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * One bindable row of the Admin holiday-import form.
 */
@Getter
@Setter
public class HolidaySelectionForm {

    private boolean selected;
    private String uuid;
    private boolean dayOff;
}