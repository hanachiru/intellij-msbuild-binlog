# MSBuild Binlog Viewer for Rider

[English README](README.md)

MSBuild Binlog Viewer for Rider は、`msbuild.binlog` を Rider 内で可視化することができるプラグインです。

## できること

- `.binlog` を専用エディタで開く
- Build / Project / Target / Task / Message などをツリー表示する
- ツリーをテキストで絞り込む
- 選択ノードの情報をコンパクトな properties パネルで確認する

## 使い方

1. Rider に plugin をインストールします。
2. `msbuild.binlog` を開きます。
3. 検索ボックスでツリーを絞り込みます。
4. ノードを選択して properties を確認します。

## 補足

- Structured Log Viewer の全機能を再現するのではなく、Rider 上で扱いやすいコンパクトな viewer を目指しています。
- build tree は `MSBuild.StructuredLogger` をベースに生成しています。