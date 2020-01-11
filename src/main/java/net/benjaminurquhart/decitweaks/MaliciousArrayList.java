package net.benjaminurquhart.decitweaks;

import java.util.ArrayList;

public class MaliciousArrayList<T> extends ArrayList<T> {

	private static final long serialVersionUID = -8735234821563565167L;
	
	@Override
	public int size() {
		return super.size() + 1000;
	}

}
