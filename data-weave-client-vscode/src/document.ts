import * as vscode from "vscode";
import { Uri, Range, DecorationRenderOptions } from "vscode";
import { EditorDecoration, EditorDecorationParams } from "./interfaces/editorDecoration";

export function openTextDocument(
    uri: string,
    range?: Range) {
    const documentUri = Uri.parse(uri);
    vscode.workspace.openTextDocument(documentUri)
        .then(document => {
            console.log("opening " + documentUri);
            vscode.window.showTextDocument(document, { selection: range });
        });
}

export function findVisibleEditor(uri: string): vscode.TextEditor | null {
    const foundEditor = vscode.window.visibleTextEditors.find((editor) => {
        return editor.document.uri.toString() == Uri.parse(uri).toString();
    });
    return foundEditor || null;
}


const decorationSettings: DecorationRenderOptions = {};


let decorationType = vscode.window.createTextEditorDecorationType(decorationSettings);

export function clearDecorations() {
    decorationType.dispose();
    decorationType = vscode.window.createTextEditorDecorationType(decorationSettings);
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
                    fontStyle: "italic",                    
                    color: editorDecoration.color
                }
            }
        };
        decorations.push(decoration);
    });
    return decorations;
}

