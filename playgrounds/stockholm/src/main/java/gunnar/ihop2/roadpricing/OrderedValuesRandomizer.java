package gunnar.ihop2.roadpricing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 
 * @author Gunnar Flötteröd
 *
 */
public class OrderedValuesRandomizer {

	// -------------------- INTERFACE DEFINITION --------------------

	public static interface ValueRandomizer {

		// public double newConstrained();

		public double newUnconstrained(double oldValue);

		public double constrain(double originalValue);
	}

	// -------------------- MEMBERS --------------------

	private final ValueRandomizer valueRandomizer;

	// -------------------- CONSTRUCTION --------------------

	public OrderedValuesRandomizer(final ValueRandomizer valueRandomizer) {
		this.valueRandomizer = valueRandomizer;
	}

	// -------------------- IMPLEMENTATION --------------------

	// public List<Double> newRandomized(final int size) {
	// List<Double> result;
	// do {
	// result = new ArrayList<>(size);
	// for (int i = 0; i < size; i++) {
	// result.add(this.valueRandomizer.newConstrained());
	// }
	// } while (result.size() != size);
	// Collections.sort(result);
	// return result;
	// }

	public List<List<Double>> newRandomizedPair(final List<Double> values) {
		List<Double> result1;
		List<Double> result2;
		do {
			result1 = new ArrayList<>(values.size());
			result2 = new ArrayList<>(values.size());
			for (Double value : values) {
				final double delta = this.valueRandomizer
						.newUnconstrained(value) - value;
				result1.add(this.valueRandomizer.constrain(value + delta));
				result2.add(this.valueRandomizer.constrain(value - delta));
			}
		} while ((result1.size() != values.size())
				|| (result2.size() != values.size()));

		Collections.sort(result1);
		Collections.sort(result2);

		final List<List<Double>> resultPair = new ArrayList<>();
		resultPair.add(result1);
		resultPair.add(result2);
		return resultPair;
	}
}