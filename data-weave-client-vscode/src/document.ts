import * as vscode from "vscode";
import {Uri} from "vscode";
import {EditorDecoration, EditorDecorationParams} from "./interfaces/editorDecoration";

export function openTextDocument(uri: string) {
    const documentUri = Uri.parse(uri);
    vscode.workspace.openTextDocument(documentUri)
        .then(document => {
            console.log("opening " + documentUri);
            vscode.window.showTextDocument(document);
        });
}

export function findVisibleEditor(uri: string): vscode.TextEditor | null {
    const foundEditor = vscode.window.visibleTextEditors.find((editor) => {
        return editor.document.uri.toString() == Uri.parse(uri).toString();
    });
    return foundEditor || null;
}

let decorationType = vscode.window.createTextEditorDecorationType({});
export function clearDecorations() {
    decorationType.dispose();
    decorationType = vscode.window.createTextEditorDecorationType({});
}

export function setDecorations(params: EditorDecorationParams) {
    let editor = findVisibleEditor(params.documentUri);
    if (editor) {
        const decorations = toVsDecorations(params.decorations);
        editor.setDecorations(decorationType, decorations);
    }
}

function toVsDecorations(editorDecorations: EditorDecoration[]): vscode.DecorationOptions[] {
    let decorations = [];
    editorDecorations.forEach((editorDecoration) => {
        const decoration: vscode.DecorationOptions = {
            range: editorDecoration.range,
            renderOptions: {
                after: {
                    contentText: editorDecoration.text,
                    color: "grey"
                }
            }
        };
        decorations.push(decoration);
    });
    return decorations;
}

