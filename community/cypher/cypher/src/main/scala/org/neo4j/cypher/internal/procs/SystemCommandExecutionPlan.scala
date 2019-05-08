/*
 * Copyright (c) 2002-2019 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.cypher.internal.procs

import org.neo4j.cypher.internal.compatibility.v4_0.ExceptionTranslatingQueryContext
import org.neo4j.cypher.internal.plandescription.Argument
import org.neo4j.cypher.internal.result.InternalExecutionResult
import org.neo4j.cypher.internal.runtime.interpreted.TransactionBoundQueryContext
import org.neo4j.cypher.internal.runtime.{InputDataStream, QueryContext}
import org.neo4j.cypher.internal.v4_0.util.InternalNotification
import org.neo4j.cypher.internal.{ExecutionEngine, ExecutionPlan, RuntimeName, SystemCommandRuntimeName}
import org.neo4j.cypher.result.RuntimeResult
import org.neo4j.graphdb.QueryStatistics
import org.neo4j.kernel.impl.query.QuerySubscriber
import org.neo4j.values.AnyValue
import org.neo4j.values.virtual.MapValue

/**
  * Execution plan for performing system commands, i.e. creating databases or showing roles and users.
  */
case class SystemCommandExecutionPlan(name: String, normalExecutionEngine: ExecutionEngine, query: String, systemParams: MapValue, onError: Throwable => {} = e => throw e)
  extends ExecutionPlan {

  override def run(ctx: QueryContext,
                   doProfile: Boolean,
                   params: MapValue,
                   prePopulateResults: Boolean,
                   ignore: InputDataStream,
                   subscriber: QuerySubscriber): RuntimeResult = {

    val tc = ctx.asInstanceOf[ExceptionTranslatingQueryContext].inner.asInstanceOf[TransactionBoundQueryContext].transactionalContext.tc
    val execution = normalExecutionEngine.execute(query, systemParams, tc, doProfile, prePopulateResults, new SystemCommandQuerySubscriber(subscriber, onError))
    SystemCommandRuntimeResult(ctx, subscriber, execution.asInstanceOf[InternalExecutionResult])
  }

  override def runtimeName: RuntimeName = SystemCommandRuntimeName

  override def metadata: Seq[Argument] = Nil

  override def notifications: Set[InternalNotification] = Set.empty
}

/**
  * A wrapping QuerySubscriber that overrides the error handling to allow custom error messages for SystemCommands instead of the inner errors.
  */
class SystemCommandQuerySubscriber(inner: QuerySubscriber, doOnError: Throwable => {}) extends QuerySubscriber {
  override def onResult(numberOfFields: Int): Unit = inner.onResult(numberOfFields)
  override def onResultCompleted(statistics: QueryStatistics): Unit = inner.onResultCompleted(statistics)
  override def onRecord(): Unit = inner.onRecord()
  override def onRecordCompleted(): Unit = inner.onRecordCompleted()
  override def onField(offset: Int, value: AnyValue): Unit = inner.onField(offset, value)
  override def onError(throwable: Throwable): Unit = doOnError(throwable)
  override def equals(obj: Any): Boolean = inner.equals(obj)
}
