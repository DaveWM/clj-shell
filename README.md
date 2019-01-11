# clj-shell

## Rationale

`clj-shell` provides some unix-command-esque utility functions, primarily for use from the repl.
The names of the functions mirror unix commands where possible, except using clojure naming conventions (so `cd` becomes `cd!` for example). 
The aim of this library is not to provide full shell scripting functionality, but just to give users a quick way of executing some common shell commands without leaving the repl.

Note that this library does not attempt to provide functions for _every_ unix command.
Here are some common functions which `clj-shell` does not provide, and a recommended library to use instead:
* `grep` - see [multigrep](https://github.com/pmonks/multigrep)
* `ssh` - see [clj-ssh](https://github.com/hugoduncan/clj-ssh)
* `ps`/`top`/`df` - see [sigmund](https://github.com/zcaudate-me/sigmund)
* Running arbitrary programs - see [conch](https://github.com/Raynes/conch)
 
## Usage

* Add `[clj-shell "0.1.0-SNAPSHOT"]` to your `project.clj`
* Start a repl, and enter `(use 'clj-shell.core)`
* See the docs for a list of available functions

## License

Distributed under the [GPL V3 license](https://www.gnu.org/licenses/gpl-3.0.en.html).
