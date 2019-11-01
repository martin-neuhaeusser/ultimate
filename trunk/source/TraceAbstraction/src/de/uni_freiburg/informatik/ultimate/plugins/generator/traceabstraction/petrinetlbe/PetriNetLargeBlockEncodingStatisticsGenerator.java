/*
 * Copyright (C) 2019 Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * Copyright (C) 2019 University of Freiburg
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
package de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.petrinetlbe;

import java.util.Collection;

import de.uni_freiburg.informatik.ultimate.util.statistics.IStatisticsDataProvider;
import de.uni_freiburg.informatik.ultimate.util.statistics.IStatisticsType;
import de.uni_freiburg.informatik.ultimate.util.statistics.StatisticsGeneratorWithStopwatches;

public class PetriNetLargeBlockEncodingStatisticsGenerator extends StatisticsGeneratorWithStopwatches
		implements IStatisticsDataProvider {

	private int mMoverChecksPositive = 0;
	private int mMoverChecksNegative = 0;
	private int mMoverChecksUnknown = 0;
	private int mMoverChecksTotal = 0;
	// mCheckedPairsTotal != mMoverChecksTotal, because if something had been checked, it won't be checked again.
	private int mCheckedPairsTotal = 0;
	private int mTotalNumberOfCompositions = 0;
	private int mProgramPointsBefore = -1;
	private int mProgramPointsAfterwards = -1;
	private int mTransitionsBefore = -1;
	private int mTransitionsAfterwards = -1;

	public PetriNetLargeBlockEncodingStatisticsGenerator() {
	}

	@Override
	public IStatisticsType getBenchmarkType() {
		return PetriNetLargeBlockEncodingStatisticsType.getInstance();
	}

	public void reportPositiveMoverCheck(int i) {
		mMoverChecksPositive += i;
	}

	public void reportNegativeMoverCheck(int i) {
		mMoverChecksNegative += i;
	}

	public void reportUnkonwnMoverCheck() {
		mMoverChecksUnknown++;
	}
	
	public void reportMoverChecksTotal(int i) {
		mMoverChecksTotal += i;
	}
	
	public void reportCheckedPairsTotal(int i) {
		mCheckedPairsTotal += i;
	}
	
	public void reportTotalNumberOfCompositions(int i) {
		mTotalNumberOfCompositions += i;
	}

	public void setProgramPointsBefore(final int programPointsBefore) {
		mProgramPointsBefore = programPointsBefore;
	}

	public void setProgramPointsAfterwards(final int programPointsAfterwards) {
		mProgramPointsAfterwards = programPointsAfterwards;
	}

	public void setTransitionsBefore(final int transitionsBefore) {
		mTransitionsBefore = transitionsBefore;
	}

	public void setTransitionsAfterwards(final int transitionsAfterwards) {
		mTransitionsAfterwards = transitionsAfterwards;
	}

	@Override
	public Object getValue(final String key) {
		final PetriNetLargeBlockEncodingStatisticsDefinitions keyEnum = Enum
				.valueOf(PetriNetLargeBlockEncodingStatisticsDefinitions.class, key);
		switch (keyEnum) {
		case LbeTime:
			try {
				return getElapsedTime(key);
			} catch (final StopwatchStillRunningException e) {
				throw new AssertionError("clock still running: " + key);
			}
		case MoverChecksNegative:
			return mMoverChecksNegative;
		case MoverChecksPositive:
			return mMoverChecksPositive;
		case MoverChecksUnknown:
			return mMoverChecksUnknown;
		case MoverChecksTotal:
			return mMoverChecksTotal;
		case CheckedPairsTotal:
			return mCheckedPairsTotal;
		case TotalNumberOfCompositions:
			return mTotalNumberOfCompositions;
		case ProgramPointsAfterwards:
			return mProgramPointsAfterwards;
		case ProgramPointsBefore:
			return mProgramPointsBefore;
		case TransitionsAfterwards:
			return mTransitionsAfterwards;
		case TransitionsBefore:
			return mTransitionsBefore;
		default:
			throw new AssertionError("unknown data");
		}
	}

	@Override
	public String[] getStopwatches() {
		return new String[] { PetriNetLargeBlockEncodingStatisticsDefinitions.LbeTime.toString() };
	}

	@Override
	public Collection<String> getKeys() {
		return PetriNetLargeBlockEncodingStatisticsType.getInstance().getKeys();
	}

}