# MSBuild Binlog Viewer for Rider

[![Releases](https://img.shields.io/github/release/hanachiru/intellij-msbuild-binlog.svg)](https://github.com/hanachiru/intellij-msbuild-binlog/releases)
[![license](https://img.shields.io/badge/LICENSE-MIT-green.svg)](LICENSE)
[![test](https://github.com/hanachiru/intellij-msbuild-binlog/actions/workflows/test.yml/badge.svg)](https://github.com/hanachiru/intellij-msbuild-binlog/actions/workflows/test.yml)

[日本語版 README](README.ja.md)

MSBuild Binlog Viewer for Rider opens `msbuild.binlog` files in a dedicated Rider editor with a compact tree view and a focused property inspector.

![Screenshot of MSBuild Binlog Viewer for Rider](docs/Sample.png)

## Features

- Browse Build / Project / Target / Task / Message nodes as a tree
- Filter the tree by text

## Requirements

- Rider 2026.1 or newer
- .NET 10 or newer

## Usage

1. Install the plugin in Rider.
2. Open a `msbuild.binlog` file.
