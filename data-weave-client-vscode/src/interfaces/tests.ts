import { Range } from "vscode";
import { NotificationType, RequestType } from "vscode-languageserver-protocol";

export namespace WeavePublishTests {
    export const type = new NotificationType<
        WeaveTestItemsParams,
        void
    >("weave/tests/publishTestItems");

    export interface WeaveTestItemsParams {
        rootTestItems: Array<WeaveTestItem>
    }

    export interface WeaveTestItem {
        id: string,
        label: string,
        uri: string,
        range?: Range,
        children: Array<WeaveTestItem>
    }
}

export namespace WeavePushTestResult {
    export const type = new NotificationType<
        WeaveTestNotificationParam,
        void
    >("weave/tests/publishTestResults");

    export interface WeaveTestNotificationParam {
        event: string,
        message: string,
        name: string,
        duration: number,
        locationHint: string,
        status: string
    }

}