/*
 * Copyright (C) 2016 Christian Schilling (schillic@informatik.uni-freiburg.de)
 * Copyright (C) 2016 University of Freiburg
 *
 * This file is part of the ULTIMATE TraceAbstraction plug-in.
 *
 * The ULTIMATE TraceAbstraction plug-in is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE TraceAbstraction plug-in is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE TraceAbstraction plug-in. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE TraceAbstraction plug-in, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE TraceAbstraction plug-in grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.tracehandling.strategy;

import java.util.ArrayList;
import java.util.List;

import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.cfg.structure.IIcfgTransition;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.smt.tracecheck.TraceCheckReasonUnknown.RefinementStrategyExceptionBlacklist;
import de.uni_freiburg.informatik.ultimate.lib.tracecheckerutils.singletracecheck.InterpolatingTraceCheck;
import de.uni_freiburg.informatik.ultimate.lib.tracecheckerutils.singletracecheck.InterpolationTechnique;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.interpolantautomata.builders.StraightLineInterpolantAutomatonBuilder;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.preferences.TraceAbstractionPreferenceInitializer.RefinementStrategy;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.tracehandling.IIpTcStrategyModule;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.tracehandling.IRefinementStrategy;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.tracehandling.StrategyModuleFactory;

/**
 * {@link IRefinementStrategy} that first tries an {@link InterpolatingTraceCheck} using
 * {@link InterpolationTechnique#Craig_TreeInterpolation} and then {@link InterpolationTechnique#FPandBP}.
 *
 * The class uses a {@link StraightLineInterpolantAutomatonBuilder} for constructing the interpolant automaton.
 *
 * @author Christian Schilling (schillic@informatik.uni-freiburg.de)
 */
public class CamelRefinementStrategy<L extends IIcfgTransition<?>> extends BasicRefinementStrategy<L> {

	public CamelRefinementStrategy(final StrategyModuleFactory<L> factory,
			final RefinementStrategyExceptionBlacklist exceptionBlacklist) {
		super(factory, createModules(factory), factory.createIpAbStrategyModuleStraightlineAll(), exceptionBlacklist);
	}

	@SuppressWarnings("unchecked")
	static <L extends IIcfgTransition<?>> IIpTcStrategyModule<?, L>[]
			createModules(final StrategyModuleFactory<L> factory) {

		final List<IIpTcStrategyModule<?, L>> rtr = new ArrayList<>();
		rtr.add(factory.createIpTcStrategyModuleSmtInterpolCraig(InterpolationTechnique.Craig_NestedInterpolation));
		rtr.add(factory.createIpTcStrategyModuleZ3(InterpolationTechnique.FPandBPonlyIfFpWasNotPerfect));
		return rtr.toArray(new IIpTcStrategyModule[rtr.size()]);
	}

	@Override
	public String getName() {
		return RefinementStrategy.CAMEL.toString();
	}
}
