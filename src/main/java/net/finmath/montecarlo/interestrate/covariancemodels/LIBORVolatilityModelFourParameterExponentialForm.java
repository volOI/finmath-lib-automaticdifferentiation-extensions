/*
 * (c) Copyright Christian P. Fries, Germany. Contact: email@christian-fries.de.
 *
 * Created on 08.08.2005
 */
package net.finmath.montecarlo.interestrate.covariancemodels;

import net.finmath.montecarlo.AbstractRandomVariableFactory;
import net.finmath.montecarlo.RandomVariable;
import net.finmath.montecarlo.RandomVariableFactory;
import net.finmath.stochastic.RandomVariableInterface;
import net.finmath.time.TimeDiscretizationInterface;

/**
 * Implements the volatility model
 * \[
 * 	\sigma_{i}(t_{j}) = ( a + b (T_{i}-t_{j}) ) exp(-c (T_{i}-t_{j})) + d \text{.}
 * \]
 * 
 * The parameters here have some interpretation:
 * <ul>
 * <li>The parameter a: an initial volatility level.</li>
 * <li>The parameter b: the slope at the short end (shortly before maturity).</li>
 * <li>The parameter c: exponential decay of the volatility in time-to-maturity.</li>
 * <li>The parameter d: if c &gt; 0 this is the very long term volatility level.</li>
 * </ul>
 *
 * Note that this model results in a terminal (Black 76) volatility which is given
 * by
 * \[
 * 	\left( \sigma^{\text{Black}}_{i}(t_{k}) \right)^2 = \frac{1}{t_{k}} \sum_{j=0}^{k-1} \left( ( a + b (T_{i}-t_{j}) ) exp(-c (T_{i}-t_{j})) + d \right)^{2} (t_{j+1}-t_{j})
 * \]
 * i.e., the instantaneous volatility is given by the picewise constant approximation of the function
 * \[
 * 	\sigma_{i}(t) = ( a + b (T_{i}-t) ) exp(-c (T_{i}-t)) + d
 * \]
 * on the time discretization \( \{ t_{j} \} \). For the exact integration of this formula see {@link LIBORVolatilityModelFourParameterExponentialFormIntegrated}.
 * 
 * @author Christian Fries
 */
public class LIBORVolatilityModelFourParameterExponentialForm extends LIBORVolatilityModel {

	private final AbstractRandomVariableFactory	randomVariableFactory;

	private RandomVariableInterface a;
	private RandomVariableInterface b;
	private RandomVariableInterface c;
	private RandomVariableInterface d;

	private boolean isCalibrateable = false;

	// A lazy init cache
	private transient RandomVariableInterface[][] volatility;
	private Object volatilityLazyInitLock = new Object();

	/**
	 * Creates the volatility model &sigma;<sub>i</sub>(t<sub>j</sub>) = ( a + b * (T<sub>i</sub>-t<sub>j</sub>) ) * exp(-c (T<sub>i</sub>-t<sub>j</sub>)) + d
	 * 
	 * @param randomVariableFactory The random variable factor used to construct random variables from the parameters. 
	 * @param timeDiscretization The simulation time discretization t<sub>j</sub>.
	 * @param liborPeriodDiscretization The period time discretization T<sub>i</sub>.
	 * @param a The parameter a: an initial volatility level.
	 * @param b The parameter b: the slope at the short end (shortly before maturity).
	 * @param c The parameter c: exponential decay of the volatility in time-to-maturity.
	 * @param d The parameter d: if c &gt; 0 this is the very long term volatility level.
	 * @param isCalibrateable Set this to true, if the parameters are available for calibration.
	 */
	public LIBORVolatilityModelFourParameterExponentialForm(AbstractRandomVariableFactory randomVariableFactory, TimeDiscretizationInterface timeDiscretization, TimeDiscretizationInterface liborPeriodDiscretization, RandomVariableInterface a, RandomVariableInterface b, RandomVariableInterface c, RandomVariableInterface d, boolean isCalibrateable) {
		super(timeDiscretization, liborPeriodDiscretization);
		this.randomVariableFactory = randomVariableFactory;
		this.a = a;
		this.b = b;
		this.c = c;
		this.d = d;
		this.isCalibrateable = isCalibrateable;
	}

	/**
	 * Creates the volatility model &sigma;<sub>i</sub>(t<sub>j</sub>) = ( a + b * (T<sub>i</sub>-t<sub>j</sub>) ) * exp(-c (T<sub>i</sub>-t<sub>j</sub>)) + d
	 * 
	 * @param timeDiscretization The simulation time discretization t<sub>j</sub>.
	 * @param liborPeriodDiscretization The period time discretization T<sub>i</sub>.
	 * @param a The parameter a: an initial volatility level.
	 * @param b The parameter b: the slope at the short end (shortly before maturity).
	 * @param c The parameter c: exponential decay of the volatility in time-to-maturity.
	 * @param d The parameter d: if c &gt; 0 this is the very long term volatility level.
	 * @param isCalibrateable Set this to true, if the parameters are available for calibration.
	 */
	public LIBORVolatilityModelFourParameterExponentialForm(TimeDiscretizationInterface timeDiscretization, TimeDiscretizationInterface liborPeriodDiscretization, RandomVariableInterface a, RandomVariableInterface b, RandomVariableInterface c, RandomVariableInterface d, boolean isCalibrateable) {
		this(new RandomVariableFactory(), timeDiscretization, liborPeriodDiscretization, a, b, c, d, isCalibrateable);
	}

	@Override
	public RandomVariableInterface[] getParameter() {
		if(!isCalibrateable) return null;

		RandomVariableInterface[] parameter = new RandomVariableInterface[4];
		parameter[0] = a;
		parameter[1] = b;
		parameter[2] = c;
		parameter[3] = d;

		return parameter;
	}

	@Override
	public void setParameter(RandomVariableInterface[] parameter) {
		if(!isCalibrateable) return;

		this.a = parameter[0];
		this.b = parameter[1];
		this.c = parameter[2];
		this.d = parameter[3];

		// Invalidate cache
		volatility = null;
	}

	@Override
	public RandomVariableInterface getVolatility(int timeIndex, int liborIndex) {

		synchronized (volatilityLazyInitLock) {
			if(volatility == null) volatility = new RandomVariableInterface[getTimeDiscretization().getNumberOfTimeSteps()][getLiborPeriodDiscretization().getNumberOfTimeSteps()];

			if(volatility[timeIndex][liborIndex] == null) {
				double time             = getTimeDiscretization().getTime(timeIndex);
				double maturity         = getLiborPeriodDiscretization().getTime(liborIndex);
				double timeToMaturity   = maturity-time;

				RandomVariableInterface volatilityInstanteaneous; 
				if(timeToMaturity <= 0)
				{
					volatilityInstanteaneous = randomVariableFactory.createRandomVariable(0.0);   // This forward rate is already fixed, no volatility
				}
				else
				{
					volatilityInstanteaneous = (a.addProduct(b, timeToMaturity)).mult(c.mult(-timeToMaturity).exp()).add(d);
				}

				volatility[timeIndex][liborIndex] = volatilityInstanteaneous;
			}

			return volatility[timeIndex][liborIndex];
		}
	}

	@Override
	public Object clone() {
		return new LIBORVolatilityModelFourParameterExponentialForm(
				super.getTimeDiscretization(),
				super.getLiborPeriodDiscretization(),
				a,
				b,
				c,
				d,
				isCalibrateable
				);
	}
}
