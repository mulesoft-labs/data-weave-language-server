import * as vscode from "vscode";
import {Uri} from "vscode";
import {EditorDecoration, EditorDecorationParams, EditorPosition, EditorRange} from "./interfaces/editorDecoration";

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

export function setDecorations(options: EditorDecorationParams) {
    let editor = findVisibleEditor(options.documentUri);
    if (editor) {
        const decorations = toVsDecorations(options.decorations);
        editor.setDecorations(decorationType, decorations);
    }
}

function toVsDecorations(editorDecorations: EditorDecoration[]): vscode.Range[] |  vscode.DecorationOptions[] {
    let decorations = [];
    editorDecorations.forEach((editorDecoration) => {
        const decoration: vscode.Range | vscode.DecorationOptions = {
            range: toVsRange(editorDecoration.range),

            renderOptions: {
                after: {
                    contentText: editorDecoration.text,
                    color: "gray",

                }
            }
        };
        decorations.push(decoration);
    });
    return decorations;
}

function toVsRange(range: EditorRange): vscode.Range {
    return new vscode.Range(
        toVsPosition(range.start),
        toVsPosition(range.end)
    );
}
function toVsPosition(position: EditorPosition): vscode.Position {
    return new vscode.Position(position.line, position.column);
}