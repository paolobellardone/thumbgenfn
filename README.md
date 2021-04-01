# Serverless Thumbnail Generator

Initialize your function

```sh
fn init --runtime java [function-name]
```

Switch into the generated directory

```sh
cd [function-name]
```

Create a context for this compartment and select it for use

```sh
fn create context [compartment-name] --provider oracle
fn use context [compartment-name]
```

Update the context with the compartment ID and the Oracle Functions API URL

```sh
fn update context [compartment-id]
fn update context api-url https://functions.[region-id].oraclecloud.com
```

Update the context with the location of the Registry you want to use

```sh
fn update context registry [region-key].ocir.io/[tenancy-namespace]/[ocir-repo]
```

Log into the Registry using the Auth Token as your password

```sh
docker login -u '[tenancy-namespace]/[username]' [region-key].ocir.io
```

Deploy your function

```sh
fn deploy --app [application-name]
```

Invoke your function

```sh
fn invoke [application-name] [function-name]
```

## License

MIT License

Copyright (c) 2021 PaoloB

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
