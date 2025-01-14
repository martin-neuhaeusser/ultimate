package de.uni_freiburg.informatik.ultimate.lib.smtlibutils.bvinttranslation;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.LinkedHashMap;

import de.uni_freiburg.informatik.ultimate.lib.smtlibutils.BitvectorUtils;
import de.uni_freiburg.informatik.ultimate.lib.smtlibutils.ManagedScript;
import de.uni_freiburg.informatik.ultimate.lib.smtlibutils.QuantifierUtils;
import de.uni_freiburg.informatik.ultimate.lib.smtlibutils.SmtSortUtils;
import de.uni_freiburg.informatik.ultimate.lib.smtlibutils.SmtUtils;
import de.uni_freiburg.informatik.ultimate.logic.ApplicationTerm;
import de.uni_freiburg.informatik.ultimate.logic.ConstantTerm;
import de.uni_freiburg.informatik.ultimate.logic.FunctionSymbol;
import de.uni_freiburg.informatik.ultimate.logic.QuantifiedFormula;
import de.uni_freiburg.informatik.ultimate.logic.Rational;
import de.uni_freiburg.informatik.ultimate.logic.Script;
import de.uni_freiburg.informatik.ultimate.logic.Sort;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.logic.TermTransformer;
import de.uni_freiburg.informatik.ultimate.logic.TermVariable;

public class BvToIntTranslation extends TermTransformer {
	// private final HashMap<Term, Term> mTranslatedTerms; // Maps Bv term to Int
	private final Script mScript;
	private final static String BITVEC_CONST_PATTERN = "bv\\d+";
	private boolean mNutzTransformation;
	private final ManagedScript mMgdScript;
	private final TermVariable[] mFreeVars;
	private final TranslationConstrainer mTc;

	private final LinkedHashMap<Term, Term> mVariableMap; // Maps BV Var to Integer Var
	private final LinkedHashMap<Term, Term> mReversedVarMap;

	public BvToIntTranslation(final ManagedScript mgdscript, final LinkedHashMap<Term, Term> variableMap,
			final TranslationConstrainer tc, final TermVariable[] freeVars) {

		mMgdScript = mgdscript;
		mScript = mgdscript.getScript();
		mNutzTransformation = false;
		mFreeVars = freeVars;
		if (variableMap != null) {
			mVariableMap = variableMap;
		} else {
			mVariableMap = new LinkedHashMap<Term, Term>();
		}

		mReversedVarMap = new LinkedHashMap<Term, Term>();
		mTc = tc;
	}

	public void setNutzTransformation(final boolean bool) {
		mNutzTransformation = bool;
	}

	@Override
	public void convert(final Term term) {
		final Sort intSort = SmtSortUtils.getIntSort(mScript);
		if (term instanceof TermVariable) {
			for (final TermVariable variable : mFreeVars) {
				if (term == variable) {
					final Term intVar = translateVars(term);
					mTc.varConstraint(term, intVar); // Create and Collect Constraints
					setResult(intVar);
					return;
				}
			}
			setResult(translateVars(term));
			return;
		} else if (term instanceof ApplicationTerm) {
			final ApplicationTerm appTerm = (ApplicationTerm) term;
			final FunctionSymbol fsym = appTerm.getFunction();
			if (appTerm.getParameters().length == 0) {
				if (SmtUtils.isConstant(appTerm)) {
					final Term intVar = translateVars(term);
					mTc.varConstraint(term, intVar); // Create and Collect Constraints
					setResult(intVar);
					return;
				}
			}

			if (fsym.isIntern()) {
				switch (fsym.getName()) {
				case "bvor": {
					// bvor = bvsub(bvadd, bvand)
					final Term bvor = mScript.term("bvsub", mScript.term("bvadd", appTerm.getParameters()),
							mScript.term("bvand", appTerm.getParameters()));
					pushTerm(bvor);
					return;
				}
				case "bvxor": {
					pushTerm(mScript.term("bvsub",
							mScript.term("bvsub", mScript.term("bvadd", appTerm.getParameters()),
									mScript.term("bvand", appTerm.getParameters())),
							mScript.term("bvand", appTerm.getParameters())));
					return;
				}
				case "sign_extend": {
					final BigInteger[] indices = new BigInteger[2];
					indices[0] = BigInteger
							.valueOf(Integer.valueOf(appTerm.getParameters()[0].getSort().getIndices()[0]) - 1);
					indices[1] = BigInteger
							.valueOf(Integer.valueOf(appTerm.getParameters()[0].getSort().getIndices()[0]) - 1);

					Term repeat = appTerm.getParameters()[0];
					final int difference = Integer.valueOf(appTerm.getSort().getIndices()[0])
							- Integer.valueOf(appTerm.getParameters()[0].getSort().getIndices()[0]);
					for (int i = 0; i < difference; i++) {
						repeat = BitvectorUtils.termWithLocalSimplification(mScript, "concat", null, BitvectorUtils
								.termWithLocalSimplification(mScript, "extract", indices, appTerm.getParameters()[0]),
								repeat);
					}

					pushTerm(repeat);
					return;

				}
				case "bvsrem": {

					final BigInteger[] indices = new BigInteger[2];
					indices[0] = BigInteger
							.valueOf(Integer.valueOf(appTerm.getParameters()[0].getSort().getIndices()[0]) - 1);
					indices[1] = BigInteger
							.valueOf(Integer.valueOf(appTerm.getParameters()[0].getSort().getIndices()[0]) - 1);
					final Term msbLhs = BitvectorUtils.termWithLocalSimplification(mScript, "extract", indices,
							appTerm.getParameters()[0]);
					final Term msbRhs = BitvectorUtils.termWithLocalSimplification(mScript, "extract", indices,
							appTerm.getParameters()[1]);

					final Term zeroVec =
							SmtUtils.rational2Term(mScript, Rational.ZERO, SmtSortUtils.getBitvectorSort(mScript, 1));
					final Term oneVec =
							SmtUtils.rational2Term(mScript, Rational.ONE, SmtSortUtils.getBitvectorSort(mScript, 1));
					final Term ifterm1 = SmtUtils.and(mScript, SmtUtils.equality(mScript, zeroVec, msbLhs),
							SmtUtils.equality(mScript, zeroVec, msbRhs));
					final Term ifterm2 = SmtUtils.and(mScript, SmtUtils.equality(mScript, oneVec, msbLhs),
							SmtUtils.equality(mScript, zeroVec, msbRhs));
					final Term ifterm3 = SmtUtils.and(mScript, SmtUtils.equality(mScript, zeroVec, msbLhs),
							SmtUtils.equality(mScript, oneVec, msbRhs));

					final Term bvurem = BitvectorUtils.termWithLocalSimplification(mScript, "bvurem", null,
							appTerm.getParameters());
					final Term thenTerm2 =
							BitvectorUtils.termWithLocalSimplification(mScript, "bvneg", null,
									BitvectorUtils.termWithLocalSimplification(
											mScript, "bvurem", null, BitvectorUtils.termWithLocalSimplification(mScript,
													"bvneg", null, appTerm.getParameters()[0]),
											appTerm.getParameters()[1]));
					final Term thenTerm3 = BitvectorUtils.termWithLocalSimplification(mScript, "bvneg", null,
							BitvectorUtils.termWithLocalSimplification(mScript, "bvurem", null,
									appTerm.getParameters()[0], BitvectorUtils.termWithLocalSimplification(mScript,
											"bvneg", null, appTerm.getParameters()[1])));

					final Term elseTerm = BitvectorUtils.termWithLocalSimplification(mScript, "bvneg", null,
							BitvectorUtils.termWithLocalSimplification(mScript, "bvurem", null,
									BitvectorUtils.termWithLocalSimplification(mScript, "bvneg", null,
											appTerm.getParameters()[0]),
									BitvectorUtils.termWithLocalSimplification(mScript, "bvneg", null,
											appTerm.getParameters()[1])));

					final Term iteChain2 = mScript.term("ite", ifterm3, thenTerm3, elseTerm);
					final Term iteChain1 = mScript.term("ite", ifterm2, thenTerm2, iteChain2);
					final Term bvsrem = mScript.term("ite", ifterm1, bvurem, iteChain1);
					pushTerm(bvsrem);
					return;
				}

				case "bvsdiv": {
					final BigInteger[] indices = new BigInteger[2];
					indices[0] = BigInteger
							.valueOf(Integer.valueOf(appTerm.getParameters()[0].getSort().getIndices()[0]) - 1);
					indices[1] = BigInteger
							.valueOf(Integer.valueOf(appTerm.getParameters()[0].getSort().getIndices()[0]) - 1);
					final Term msbLhs = BitvectorUtils.termWithLocalSimplification(mScript, "extract", indices,
							appTerm.getParameters()[0]);
					final Term msbRhs = BitvectorUtils.termWithLocalSimplification(mScript, "extract", indices,
							appTerm.getParameters()[1]);

					final Term zeroVec =
							SmtUtils.rational2Term(mScript, Rational.ZERO, SmtSortUtils.getBitvectorSort(mScript, 1));
					final Term oneVec =
							SmtUtils.rational2Term(mScript, Rational.ONE, SmtSortUtils.getBitvectorSort(mScript, 1));
					final Term ifterm1 = SmtUtils.and(mScript, SmtUtils.equality(mScript, zeroVec, msbLhs),
							SmtUtils.equality(mScript, zeroVec, msbRhs));
					final Term ifterm2 = SmtUtils.and(mScript, SmtUtils.equality(mScript, oneVec, msbLhs),
							SmtUtils.equality(mScript, zeroVec, msbRhs));
					final Term ifterm3 = SmtUtils.and(mScript, SmtUtils.equality(mScript, zeroVec, msbLhs),
							SmtUtils.equality(mScript, oneVec, msbRhs));

					final Term bvudiv = BitvectorUtils.termWithLocalSimplification(mScript, "bvudiv", null,
							appTerm.getParameters());
					final Term thenTerm2 =
							BitvectorUtils.termWithLocalSimplification(mScript, "bvneg", null,
									BitvectorUtils.termWithLocalSimplification(
											mScript, "bvudiv", null, BitvectorUtils.termWithLocalSimplification(mScript,
													"bvneg", null, appTerm.getParameters()[0]),
											appTerm.getParameters()[1]));
					final Term thenTerm3 = BitvectorUtils.termWithLocalSimplification(mScript, "bvneg", null,
							BitvectorUtils.termWithLocalSimplification(mScript, "bvudiv", null,
									appTerm.getParameters()[0], BitvectorUtils.termWithLocalSimplification(mScript,
											"bvneg", null, appTerm.getParameters()[1])));

					final Term elseTerm = BitvectorUtils.termWithLocalSimplification(mScript, "bvudiv", null,
							BitvectorUtils.termWithLocalSimplification(mScript, "bvneg", null,
									appTerm.getParameters()[0]),
							BitvectorUtils.termWithLocalSimplification(mScript, "bvneg", null,
									appTerm.getParameters()[1]));

					final Term iteChain2 = mScript.term("ite", ifterm3, thenTerm3, elseTerm);
					final Term iteChain1 = mScript.term("ite", ifterm2, thenTerm2, iteChain2);
					final Term bvsdiv = mScript.term("ite", ifterm1, bvudiv, iteChain1);
					pushTerm(bvsdiv);
					return;
				}
				}
			}
		} else if (term instanceof ConstantTerm) {
			if (SmtSortUtils.isBitvecSort(term.getSort())) {
				final ConstantTerm constTerm = (ConstantTerm) term;
				assert isBitVecSort(constTerm.getSort());
				BigInteger constValue;
				if (constTerm.getValue() instanceof String) {
					String bitString = (String) constTerm.getValue();
					if (bitString.startsWith("#b")) {
						bitString = (String) constTerm.getValue();
						constValue = new BigInteger(bitString.substring(2), 2);
					} else if (bitString.startsWith("#x")) {
						constValue = new BigInteger(bitString.substring(2), 16);
					} else {
						throw new UnsupportedOperationException("Unexpected constant type");
					}
				} else {
					constValue = (BigInteger) constTerm.getValue();
				}
				final Term intConst =
						SmtUtils.rational2Term(mScript, Rational.valueOf(constValue, BigInteger.ONE), intSort);

				setResult(intConst);
				return;
			} else {
				throw new UnsupportedOperationException("unexpected constant sort");
			}
		}
		super.convert(term);

	}

	private Sort translateArraySort(final Sort sort) {
		if (SmtSortUtils.isBitvecSort(sort)) {
			return SmtSortUtils.getIntSort(mMgdScript);
		} else if (SmtSortUtils.isArraySort(sort)) {
			final Sort[] newArgs = new Sort[sort.getArguments().length];
			for (int i = 0; i < sort.getArguments().length; i++) {
				newArgs[i] = translateArraySort(sort.getArguments()[i]);
			}
			assert newArgs.length == 2;
			final Sort domainSort = newArgs[0];
			final Sort rangeSort = newArgs[1];
			return SmtSortUtils.getArraySort(mMgdScript.getScript(), domainSort, rangeSort);
		} else {
			throw new AssertionError("Unexpected Sort: " + sort);
		}
	}

	private Term translateVars(final Term term) {
		if (mVariableMap.containsKey(term)) {
			mReversedVarMap.put(mVariableMap.get(term), term);
			return mVariableMap.get(term);
		} else {
			final Sort sort = term.getSort();
			if (SmtSortUtils.isArraySort(sort)) {
				Term arrayVar;
				arrayVar = mMgdScript.constructFreshTermVariable("arrayVar", translateArraySort(sort));
				if (!(term instanceof TermVariable)) {
					arrayVar = SmtUtils.termVariable2constant(mScript, (TermVariable) arrayVar, true);
				}
				mVariableMap.put(term, arrayVar);
				mReversedVarMap.put(arrayVar, term);
				return arrayVar;
			} else {
				Term intVar;
				intVar = mMgdScript.constructFreshTermVariable("intVar", SmtSortUtils.getIntSort(mScript));
				if (!(term instanceof TermVariable)) {
					intVar = SmtUtils.termVariable2constant(mScript, (TermVariable) intVar, true);
				}
				mVariableMap.put(term, intVar);
				mReversedVarMap.put(intVar, term);
				return intVar;
			}

		}

	}

	@Override
	public void postConvertQuantifier(final QuantifiedFormula old, final Term newBody) {
		final HashSet<TermVariable> newTermVars = new HashSet();
		final HashSet<Term> tvConstraints = new HashSet();
		if (newBody != old.getSubformula()) {
			for (int i = 0; i < old.getVariables().length; i++) {
				if (SmtSortUtils.isBitvecSort(old.getVariables()[i].getSort())) {
					newTermVars.add((TermVariable) mVariableMap.get(old.getVariables()[i]));
					tvConstraints
							.add(mTc.getTvConstraint(old.getVariables()[i], mVariableMap.get(old.getVariables()[i])));

				} else {
					newTermVars.add(old.getVariables()[i]);
				}
			}
			setResult(SmtUtils.quantifier(mScript, old.getQuantifier(), newTermVars,
					QuantifierUtils.applyDualFiniteConnective(mScript, old.getQuantifier(), newBody, QuantifierUtils
							.negateIfUniversal(mScript, old.getQuantifier(), SmtUtils.and(mScript, tvConstraints)))));
			return;
		} else {
			super.postConvertQuantifier(old, newBody);
		}
	}

	/*
	 * TODO binarize or deal with equalities, additions etc with more than 2 argmuents
	 * TODO bvsdiv bvsrem // testen ob auf argumente signed to unsgined angewendet werfden muss
	 * TODO check if we have to do sth for extend's
	 * TODO modularer
	 */
	@Override
	public void convertApplicationTerm(final ApplicationTerm appTerm, final Term[] args) {
		final Sort intSort = SmtSortUtils.getIntSort(mScript);
		final BigInteger two = BigInteger.valueOf(2);
		final FunctionSymbol fsym = appTerm.getFunction();
		if (fsym.isIntern()) {
			switch (fsym.getName()) {
			case "and":
			case "or":
			case "not":
			case "=>":
			case "store":
			case "select": {
				setResult(mScript.term(fsym.getName(), args));
				return;
			}
			}
		}

		if (appTerm.getParameters().length == 0) {
			if (fsym.getName().matches(BITVEC_CONST_PATTERN)) {
				final BigInteger constValue = new BigInteger(fsym.getName().substring(2));
				final Term intConst =
						SmtUtils.rational2Term(mScript, Rational.valueOf(constValue, BigInteger.ONE), intSort);

				setResult(intConst);
				return;
			} else if (SmtUtils.isFalseLiteral(appTerm)) {
				setResult(appTerm);
				return;
			} else if (SmtUtils.isTrueLiteral(appTerm)) {
				setResult(appTerm);
				return;
			} else {
				setResult(mVariableMap.get(appTerm));
				return;
			}
		} else if (appTerm.getParameters().length == 1
				&& SmtSortUtils.isBitvecSort(appTerm.getParameters()[0].getSort())) {
			final int width = Integer.valueOf(appTerm.getParameters()[0].getSort().getIndices()[0]);
			final Term maxNumber =
					SmtUtils.rational2Term(mScript, Rational.valueOf(two.pow(width), BigInteger.ONE), intSort);

			final Term translatedLHS = args[0];
			if (fsym.isIntern()) {
				switch (fsym.getName()) {
				case "bvnot": {
					final Term not = mScript.term("-", maxNumber,
							mScript.term("+", translatedLHS, SmtUtils.rational2Term(mScript, Rational.ONE, intSort)));
					setResult(not);
					return;
				}
				case "bvneg": {
					final Term negation = mScript.term("-", maxNumber, translatedLHS);
					if (mNutzTransformation) {
						setResult(negation);
						return;
					}
					setResult(mScript.term("mod", negation, maxNumber));
					return;
				}
				case "extract": {
					if (mNutzTransformation) {
						throw new UnsupportedOperationException("TODO Nutz" + fsym.getName());
					}
					final int lowerIndex = Integer.parseInt(appTerm.getFunction().getIndices()[1]);
					final int upperIndex = Integer.parseInt(appTerm.getFunction().getIndices()[0]);

					final Term divby = SmtUtils.rational2Term(mScript,
							Rational.valueOf(two.pow(lowerIndex), BigInteger.ONE), intSort);
					final Term modby = SmtUtils.rational2Term(mScript,
							Rational.valueOf(two.pow(upperIndex - lowerIndex + 1), BigInteger.ONE), intSort);
					setResult(mScript.term("mod", mScript.term("div", translatedLHS, divby), modby));
					return;
				}
				case "zero_extend":
					setResult(args[0]);
					return;
				case "const":
					setResult(mScript.term("const", null, translateArraySort(appTerm.getSort()), args));
					return;
				case "repeat":
				case "rotate_left":
				case "rotate_right":
				default:
					throw new UnsupportedOperationException("unexpected function: " + fsym.getName());
				}
			}
		} else {
			int width = 0;
			if (SmtSortUtils.isBitvecSort(appTerm.getParameters()[0].getSort())) {
				width = Integer.valueOf(appTerm.getParameters()[1].getSort().getIndices()[0]);
			}
			final Term maxNumber =
					SmtUtils.rational2Term(mScript, Rational.valueOf(two.pow(width), BigInteger.ONE), intSort);
			final Term[] translatedArgs = args;
			if (mNutzTransformation) {
				for (int i = 0; i < args.length; i++) {
					translatedArgs[i] = mScript.term("mod", args[i], maxNumber);
				}
			}
			if (fsym.isIntern()) {
				switch (fsym.getName()) {
				case "=": {
					setResult(mScript.term("=", translatedArgs));
					return;
				}
				case "distinct": {
					setResult(mScript.term("distinct", translatedArgs));
					return;
				}
				case "bvult": {
					setResult(mScript.term("<", translatedArgs));
					return;
				}
				case "bvule": {
					setResult(mScript.term("<=", translatedArgs));
					return;
				}
				case "bvugt": {
					setResult(mScript.term(">", translatedArgs));
					return;
				}
				case "bvuge": {
					setResult(mScript.term(">=", translatedArgs));
					return;
				}
				case "bvslt": {
					final Term[] utsArgs = args;
					for (int i = 0; i < args.length; i++) {
						utsArgs[i] = uts(width, translatedArgs[i]);
					}
					setResult(mScript.term("<", utsArgs));
					return;
				}
				case "bvsle": {
					final Term[] utsArgs = args;
					for (int i = 0; i < args.length; i++) {
						utsArgs[i] = uts(width, translatedArgs[i]);
					}
					setResult(mScript.term("<=", utsArgs));
					return;
				}
				case "bvsgt": {
					final Term[] utsArgs = args;
					for (int i = 0; i < args.length; i++) {
						utsArgs[i] = uts(width, translatedArgs[i]);
					}
					setResult(mScript.term(">", utsArgs));
					return;
				}
				case "bvsge": {
					final Term[] utsArgs = args;
					for (int i = 0; i < args.length; i++) {
						utsArgs[i] = uts(width, translatedArgs[i]);
					}
					setResult(mScript.term(">=", utsArgs));
					return;
				}
				case "bvadd": {
					final Term addition = mScript.term("+", translatedArgs);
					if (mNutzTransformation) {
						setResult(addition);
						return;
					}
					setResult(mScript.term("mod", addition, maxNumber));
					return;
				}
				case "bvsub": {
					final Term substraction = mScript.term("-", translatedArgs);
					if (mNutzTransformation) {
						setResult(substraction);
						return;
					}
					setResult(mScript.term("mod", substraction, maxNumber));
					return;
				}
				case "bvmul": {
					final Term multiplication = mScript.term("*", translatedArgs);
					if (mNutzTransformation) {
						setResult(multiplication);
						return;
					}
					setResult(mScript.term("mod", multiplication, maxNumber));
					return;
				}
				case "ite": {
					setResult(mScript.term("ite", args[0], args[1], args[2]));
					return;
				}
				}

				if (appTerm.getParameters().length == 2) {
					final Term translatedLHS = translatedArgs[0];
					final Term translatedRHS = translatedArgs[1];
					switch (fsym.getName()) {
					case "bvshl": {
						final boolean iteMode = true;
						if (iteMode && !(translatedRHS instanceof ConstantTerm)) {
							Term iteChain = SmtUtils.rational2Term(mScript, Rational.ZERO, intSort);
							for (int i = width - 1; i >= 0; i--) {
								if (i == 0) {
									final Term constInt =
											SmtUtils.rational2Term(mScript, Rational.valueOf(0, 1), intSort);
									iteChain = mScript.term("ite", mScript.term("=", constInt, translatedRHS),
											translatedLHS, iteChain);
								} else {
									final Rational powResult = Rational.valueOf(i, 1);
									final Term ifTerm = mScript.term("=", translatedRHS,
											SmtUtils.rational2Term(mScript, powResult, intSort));
									final int pow = (int) Math.pow(2, i);
									final Term thenTerm =
											mScript.term("mod",
													mScript.term("*", SmtUtils.rational2Term(mScript,
															Rational.valueOf(pow, 1), intSort), translatedLHS),
													maxNumber);
									iteChain = mScript.term("ite", ifTerm, thenTerm, iteChain);
								}

							}
							setResult(iteChain);
							return;
						} else {
							final Term shift = mScript.term("*", translatedLHS, pow2(translatedRHS));
							setResult(mScript.term("mod", shift, maxNumber));
							return;
						}
					}
					case "bvlshr": {
						final boolean iteMode = true;
						if (iteMode && !(translatedRHS instanceof ConstantTerm)) {
							Term iteChain = SmtUtils.rational2Term(mScript, Rational.ZERO, intSort);
							for (int i = width - 1; i >= 0; i--) {
								if (i == 0) {
									final Term constInt =
											SmtUtils.rational2Term(mScript, Rational.valueOf(0, 1), intSort);
									iteChain = mScript.term("ite", mScript.term("=", constInt, translatedRHS),
											translatedLHS, iteChain);
								} else {
									final Rational powResult = Rational.valueOf(i, 1);
									final Term ifTerm = mScript.term("=", translatedRHS,
											SmtUtils.rational2Term(mScript, powResult, intSort));
									final int pow = (int) Math.pow(2, i);
									final Term thenTerm = mScript.term("div", translatedLHS,
											SmtUtils.rational2Term(mScript, Rational.valueOf(pow, 1), intSort));
									iteChain = mScript.term("ite", ifTerm, thenTerm, iteChain);
								}

							}
							setResult(iteChain);
							return;
						} else {
							final Term shift = mScript.term("div", translatedLHS, pow2(translatedRHS));
							setResult(shift);
							return;
						}
					}

					case "bvashr": {
						throw new UnsupportedOperationException("TODO " + fsym.getName());
					}
					case "concat": {
						final Term multiplication = mScript.term("*", translatedLHS, maxNumber);
						if (mNutzTransformation) {
							throw new UnsupportedOperationException("TODO Nutz" + fsym.getName());
						}
						setResult(mScript.term("+", multiplication, translatedRHS));
						return;
					}

					case "bvudiv": {
						Term rhs;
						Term lhs;
						if (mNutzTransformation) {
							rhs = mScript.term("mod", translatedRHS, maxNumber);
							lhs = mScript.term("mod", translatedLHS, maxNumber);
						} else {
							rhs = translatedRHS;
							lhs = translatedLHS;
						}
						final Term ifTerm =
								mScript.term("=", rhs, SmtUtils.rational2Term(mScript, Rational.ZERO, intSort));
						final Term thenTerm =
								mScript.term("-", maxNumber, SmtUtils.rational2Term(mScript, Rational.ONE, intSort));
						final Term elseTerm = mScript.term("div", lhs, rhs);
						setResult(mScript.term("ite", ifTerm, thenTerm, elseTerm));
						return;
					}

					case "bvurem": {
						Term rhs;
						Term lhs;
						if (mNutzTransformation) {
							rhs = mScript.term("mod", translatedRHS, maxNumber);
							lhs = mScript.term("mod", translatedLHS, maxNumber);
						} else {
							rhs = translatedRHS;
							lhs = translatedLHS;
						}
						final Term ifTerm =
								mScript.term("=", rhs, SmtUtils.rational2Term(mScript, Rational.ZERO, intSort));
						final Term thenTerm = lhs;
						final Term elseTerm = mScript.term("mod", lhs, rhs);
						setResult(mScript.term("ite", ifTerm, thenTerm, elseTerm));
						return;
					}
					case "bvand": {
						final Term intAnd =
								mScript.term(mTc.getIntAndFunctionSymbol().getName(), translatedLHS, translatedRHS);
						mTc.bvandConstraint(intAnd, width);
						setResult(intAnd);
						return;
					}

					}
				}
			}
		}
		super.convertApplicationTerm(appTerm, args);
	}

	private final Term uts(final int width, final Term term) {
		// 2 * (x mod 2k - 1 ) - x
		final Sort intSort = SmtSortUtils.getIntSort(mScript);

		final Term two =
				SmtUtils.rational2Term(mScript, Rational.valueOf(BigInteger.valueOf(2), BigInteger.ONE), intSort);
		final Term twoPowWidth = SmtUtils.rational2Term(mScript,
				Rational.valueOf(BigInteger.valueOf(2).pow(width - 1), BigInteger.ONE), intSort);
		final Term modulo = mScript.term("mod", term, twoPowWidth);
		return mScript.term("-", mScript.term("*", two, modulo), term);
	}

	private Term pow2(final Term term) {
		assert term.getSort().isNumericSort();
		if (term instanceof ConstantTerm) {
			final Sort intSort = SmtSortUtils.getIntSort(mScript);
			final ConstantTerm constTerm = (ConstantTerm) term;
			final Term twoPow;
			if (constTerm.getValue() instanceof Rational) {
				twoPow = SmtUtils.rational2Term(mScript, (Rational) constTerm.getValue(), intSort);
			} else {
				final BigInteger bigint = (BigInteger) constTerm.getValue();
				twoPow = SmtUtils.rational2Term(mScript,
						Rational.valueOf(BigInteger.valueOf(2).pow(bigint.intValue()), BigInteger.ONE), intSort);
			}

			return twoPow;
		}
		throw new UnsupportedOperationException("TODO");
		// return term;
	}

	private boolean isBitVecSort(final Sort sort) {
		if (sort.getName().equals("BitVec")) {
			return true;
		}
		return false;
	}

	public LinkedHashMap<Term, Term> getVarMap() {
		return mVariableMap;
	}

	public LinkedHashMap<Term, Term> getReversedVarMap() {
		return mReversedVarMap;
	}
}
