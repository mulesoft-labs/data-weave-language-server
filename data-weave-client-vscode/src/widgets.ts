import * as vscode from 'vscode';
import { QuickInputButton, QuickInputButtons, ThemeIcon, Uri } from 'vscode';
import { InputBoxOptions } from './interfaces/weaveInputBox';
import { DarkLightIconPath, IconType, IconUri, ThemeIconPath, WeaveInputButton, WeaveQuickPickItem, WeaveQuickPickParams } from './interfaces/weaveQuickPick';

//TODO add everything to disposable objects.
export function showInputBox(options: InputBoxOptions) {
    return new Promise((resolve, reject) => {
        var inputBox = vscode.window.createInputBox();
        var buttonMap: Map<QuickInputButton, string> = new Map();
        inputBox.password = options.password;
        inputBox.placeholder = options.placeHolder;
        inputBox.title = options.title;
        inputBox.ignoreFocusOut = options.ignoreFocusOut;
        inputBox.step = options.step;
        inputBox.totalSteps = options.totalSteps;
        inputBox.prompt = options.prompt;

        if(options.buttons){
            inputBox.buttons = options.buttons?.map((button) => {
                var mappedButton = mapClientButton(button);
                buttonMap.set(mappedButton, button.id);
                return mappedButton;
            })
        }

        inputBox.value = options.value;
        inputBox.onDidTriggerButton((button) => {
            resolve({ buttonPressedId: buttonMap.get(button), value: inputBox.value });
            inputBox.hide()
        })
        inputBox.onDidAccept(() => {
            resolve({ value: inputBox.value });
            inputBox.hide()
        })
        inputBox.show()
    }
    )
}

export function showQuickPick(options: WeaveQuickPickParams) {
    return new Promise((resolve, reject) => {
        var quickPick = vscode.window.createQuickPick<WeaveQuickPickItem>();
        var buttonMap: Map<QuickInputButton, string> = new Map();
        quickPick.placeholder = options.placeHolder;
        quickPick.title = options.title;
        quickPick.ignoreFocusOut = options.ignoreFocusOut;
        quickPick.step = options.step;
        quickPick.totalSteps = options.totalSteps;  
        quickPick.items = options.items

        if(options.buttons){
          quickPick.buttons = options.buttons?.map((button) => {
              var mappedButton = mapClientButton(button);
              buttonMap.set(mappedButton, button.id);
              return mappedButton;
          })
        }

        quickPick.onDidTriggerButton((button) => {
            resolve({ buttonPressedId: buttonMap.get(button), itemsId: quickPick.selectedItems.map((item) => item.id) });
            quickPick.hide()
        })
        quickPick.onDidAccept(() => {
            resolve({ itemsId: quickPick.selectedItems.map((item) => item.id) });
            quickPick.hide()
        })
        quickPick.show()
    }
    )
}

export function mapClientButton(button: WeaveInputButton): QuickInputButton {
    var path = button.iconPath
    if (button.id === "back") {
        return QuickInputButtons.Back;
    }
    switch (path.iconType) {
        case IconType.ThemeIcontType: {
            return { iconPath: new ThemeIcon((<ThemeIconPath>path).id), tooltip: button.tooltip }
        }
        case IconType.URIIconType: {
            return { iconPath: Uri.parse((<IconUri>path).uri), tooltip: button.tooltip }
        }
        case IconType.DarkLightIconPathType: {
            var darkLightPath = (<DarkLightIconPath>path)
            return { iconPath: { dark: darkLightPath.dark, light: darkLightPath.light }, tooltip: button.tooltip }
        }
    }
}