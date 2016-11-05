package edu.cmu.hcii.sugilite;

/**
 * @author toby
 * @date 10/25/16
 * @time 3:55 PM
 */
public class Const {
    //TRUE to save all the clickable items to the "app vocabulary" when recording
    public static final boolean BUILDING_VOCAB = false;

    //TRUE to enable broadcasting of accessiblity event (needed by HELPR system)
    public static final boolean BROADCASTING_ACCESSIBILITY_EVENT = true;

    //delay before running a script after clicking on OK in the VariableSetValueDialog
    public static final int SCRIPT_DELAY = 2000;

    //delay before executing each operation block in Automator
    public static final int DELAY = 3000;

    //TRUE to keep a list of all elements with text labels
    public static final boolean KEEP_ALL_TEXT_LABEL_LIST = true;


    public static final int REGISTER = 1;
    public static final int UNREGISTER = 2;
    public static final int RESPONSE = 3;
    public static final int START_TRACKING = 4;
    public static final int STOP_TRACKING = 5;
    public static final int GET_ALL_TRACKING_SCRIPTS = 6;
    public static final int GET_TRACKING_SCRIPT = 7;
    public static final int RUN = 9;
    public static final int RESPONSE_EXCEPTION = 10;
    public static final int START_RECORDING = 11;
    public static final int STOP_RECORDING = 12;
    public static final int GET_ALL_RECORDING_SCRIPTS = 13;
    public static final int GET_RECORDING_SCRIPT = 14;
    public static final int ACCESSIBILITY_EVENT = 15;
    public static final int RUN_SCRIPT = 16;
    public static final int END_RECORDING_EXCEPTION = 17;
    public static final int START_RECORDING_EXCEPTION = 18;
    public static final int FINISHED_RECORDING = 19;
    public static final int RUN_SCRIPT_EXCEPTION = 20;
    public static final int RUN_JSON = 21;
    public static final int RUN_JSON_EXCEPTION = 22;
    public static final int ADD_JSON_AS_SCRIPT = 23;
    public static final int ADD_JSON_AS_SCRIPT_EXCEPTION = 24;
    public static final int CLEAR_TRACKING_LIST = 25;
    public static final int GET_ALL_PACKAGE_VOCAB = 26;
    public static final int GET_PACKAGE_VOCAB = 27;
    public static final int MULTIPURPOSE_REQUEST = 28;
    public static final int APP_TRACKER_ID = 1001;
}
