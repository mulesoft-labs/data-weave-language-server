
export class ClientWeaveCommands {
    public static OPEN_FILE = "dw.dependency.openFile"
    public static CREATE_TEST = "dw.test.create"
    public static CREATE_MAPPING = "dw.mapping.create"
    public static CREATE_PROJECT = 'dw.project.create';
    public static ENABLE_PREVIEW = "dw.preview.enable"
    public static RUN_PREVIEW = "dw.preview.run"
    public static DISABLE_PREVIEW = "dw.preview.disable"
    public static SHOW_LOG = "dw.showLog"


    public static ADD_SCENARIO_COMMAND = "dw.scenarios.addScenario"
    public static DELETE_SCENARIO_COMMAND = "dw.scenarios.deleteScenario"
    public static DEFAULT_SCENARIO_COMMAND = "dw.scenarios.defaultScenario"

    public static ADD_INPUT_COMMAND = "dw.scenarios.addInput"
    public static DELETE_INPUT_COMMAND = "dw.scenarios.deleteInput"
}

export class ServerWeaveCommands {

    public static CREATE_TEST = "dw.createTest"
    public static CREATE_MAPPING = "dw.createMapping"
    public static ENABLE_PREVIEW = "dw.enablePreview"
    public static CREATE_SCENARIO = "dw.createScenario"
    public static DELETE_SCENARIO = "dw.deleteScenario"
    public static SET_ACTIVE_SCENARIO = "dw.activeScenario"


    public static CREATE_INPUT_SAMPLE = "dw.createInputSample"
    public static DELETE_INPUT_SAMPLE = "dw.deleteInputSample"

    public static RUN_PREVIEW = "dw.runPreview"
    public static LAUNCH_MAPPING = "dw.launchCommand"

}