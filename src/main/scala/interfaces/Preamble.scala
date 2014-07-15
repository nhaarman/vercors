/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package viper
package silicon
package interfaces

import viper.silver.components.StatefulComponent
import ast.Program
import silicon.state.terms.{Sort, Function}

trait PreambleEmitter extends StatefulComponent {
  def analyze(program: Program)
  def sorts: Set[Sort]
  def symbols: Option[Set[Function]]
  def declareSorts()
  def declareSymbols()
  def emitAxioms()
}
