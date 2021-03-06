/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package viper.silicon.supporters.functions

import viper.silver.ast
import viper.silver.ast.{Field, Predicate, FuncApp, LocationAccess}
import viper.silicon.{Map, Set, Stack}
import viper.silicon.interfaces.state.Mergeable
import viper.silicon.state.terms._

trait FunctionRecorder extends Mergeable[FunctionRecorder] {
  def data: Option[FunctionData]
  private[functions] def locToSnaps: Map[ast.LocationAccess, Set[(Stack[Term], Term)]]
  def locToSnap: Map[ast.LocationAccess, Term]
  private[functions] def fappToSnaps: Map[ast.FuncApp, Set[(Stack[Term], Term)]]
  def fappToSnap: Map[ast.FuncApp, Term]
  def freshFvfs: Set[(ast.Field, Term)]
  def freshPsfs: Set[(ast.Predicate, Term)]
  def qpTerms: Set[(Seq[Var], Stack[Term], Iterable[Term])]
  def recordSnapshot(loc: ast.LocationAccess, guards: Stack[Term], snap: Term): FunctionRecorder
  def recordSnapshot(fapp: ast.FuncApp, guards: Stack[Term], snap: Term): FunctionRecorder
  def recordQPTerms(qvars: Seq[Var], guards: Stack[Term], ts: Iterable[Term]): FunctionRecorder
  def recordFvf(field: ast.Field, fvf: Term): FunctionRecorder
  def recordPsf(predicate: ast.Predicate, psf:Term) : FunctionRecorder
}

case class ActualFunctionRecorder(private val _data: FunctionData,
                                  private[functions] val locToSnaps: Map[ast.LocationAccess, Set[(Stack[Term], Term)]] = Map(),
                                  private[functions] val fappToSnaps: Map[ast.FuncApp, Set[(Stack[Term], Term)]] = Map(),
                                  freshFvfs: Set[(ast.Field, Term)] = Set(),
                                  freshPsfs: Set[(ast.Predicate, Term)]  = Set(),
                                  qpTerms: Set[(Seq[Var], Stack[Term], Iterable[Term])] = Set())
    extends FunctionRecorder {

  val data = Some(_data)

  def locToSnap: Map[ast.LocationAccess, Term] = {
    locToSnaps.map { case (loc, guardsToSnap) =>
      /* We (arbitrarily) make the snap of the head pair (guards -> snap) of
       * guardsToSnap the inner-most else-clause, i.e., we drop the guards.
       */
      val conditionalSnap =
        guardsToSnap.tail.foldLeft(guardsToSnap.head._2) { case (tailSnap, (guards, snap)) =>
          Ite(And(guards.toSet), snap, tailSnap)
        }

      loc -> conditionalSnap
    }
  }

  def fappToSnap: Map[ast.FuncApp, Term] = {
    fappToSnaps.map { case (fapp, guardsToSnap) =>
      /* We (arbitrarily) make the snap of the head pair (guards -> snap) of
       * guardsToSnap the inner-most else-clause, i.e., we drop the guards.
       */
      val conditionalSnap =
        guardsToSnap.tail.foldLeft(guardsToSnap.head._2) { case (tailSnap, (guards, snap)) =>
          Ite(And(guards.toSet), snap, tailSnap)
        }

      fapp -> conditionalSnap
    }
  }

  def recordSnapshot(loc: ast.LocationAccess, guards: Stack[Term], snap: Term) = {
    val guardsToSnaps = locToSnaps.getOrElse(loc, Set()) + (guards -> snap)
    copy(locToSnaps = locToSnaps + (loc -> guardsToSnaps))
  }

  def recordSnapshot(fapp: ast.FuncApp, guards: Stack[Term], snap: Term) = {
    val guardsToSnaps = fappToSnaps.getOrElse(fapp, Set()) + (guards -> snap)
    copy(fappToSnaps = fappToSnaps + (fapp -> guardsToSnaps))
  }

  def recordQPTerms(qvars: Seq[Var], guards: Stack[Term], ts: Iterable[Term]) = {
    copy(qpTerms = qpTerms + ((qvars, guards, ts)))
  }

  def recordFvf(field: ast.Field, fvf: Term) = {
    copy(freshFvfs = freshFvfs + ((field, fvf)))
  }

  def recordPsf(predicate: ast.Predicate, psf: Term) = {
    copy(freshPsfs = freshPsfs + ((predicate, psf)))
  }


  def merge(other: FunctionRecorder): FunctionRecorder = {
    assert(other.getClass == this.getClass)
    assert(other.asInstanceOf[ActualFunctionRecorder]._data eq this._data)

    val lts =
      other.locToSnaps.foldLeft(locToSnaps){case (accLts, (loc, guardsToSnaps)) =>
        val guardsToSnaps1 = accLts.getOrElse(loc, Set()) ++ guardsToSnaps
        accLts + (loc -> guardsToSnaps1)
      }

    val fts =
      other.fappToSnaps.foldLeft(fappToSnaps){case (accFts, (fapp, guardsToSnaps)) =>
        val guardsToSnaps1 = accFts.getOrElse(fapp, Set()) ++ guardsToSnaps
        accFts + (fapp -> guardsToSnaps1)
      }

    val fvfs = freshFvfs ++ other.freshFvfs
    val psfs = freshPsfs ++ other.freshPsfs
    val qpts = qpTerms ++ other.qpTerms

    copy(locToSnaps = lts, fappToSnaps = fts, freshFvfs = fvfs, freshPsfs = psfs, qpTerms = qpts)
  }

  override lazy val toString = {
    val ltsStrs = locToSnaps map {case (k, v) => s"$k  |==>  $v"}
    val ftsStrs = fappToSnap map {case (k, v) => s"$k  |==>  $v"}

    s"""SnapshotRecorder(
        |  locToSnaps:
        |    ${ltsStrs.mkString("\n    ")}
        |  fappToSnap:
        |    ${ftsStrs.mkString("\n    ")}
        |)
     """.stripMargin
  }
}

case object NoopFunctionRecorder extends FunctionRecorder {
  val data = None
  private[functions] val fappToSnaps: Map[FuncApp, Set[(Stack[Term], Term)]] = Map.empty
  val fappToSnap: Map[ast.FuncApp, Term] = Map.empty
  private[functions] val locToSnaps: Map[LocationAccess, Set[(Stack[Term], Term)]] = Map.empty
  val locToSnap: Map[ast.LocationAccess, Term] = Map.empty
  val qpTerms: Set[(Seq[Var], Stack[Term], Iterable[Term])] = Set.empty
  val freshFvfs: Set[(Field, Term)] = Set.empty
  val freshPsfs: Set[(Predicate, Term)] = Set.empty
  def merge(other: FunctionRecorder): FunctionRecorder = {
    assert(other == this)

    this
  }

  def recordSnapshot(loc: LocationAccess, guards: Stack[Term], snap: Term): FunctionRecorder = this
  def recordFvf(field: Field, fvf: Term): FunctionRecorder = this
  def recordPsf(predicate: Predicate, psf: Term): FunctionRecorder = this
  def recordQPTerms(qvars: Seq[Var], guards: Stack[Term], ts: Iterable[Term]): FunctionRecorder = this
  def recordSnapshot(fapp: FuncApp, guards: Stack[Term], snap: Term): FunctionRecorder = this
}
