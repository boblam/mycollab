/**
 * This file is part of mycollab-mobile.
 *
 * mycollab-mobile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * mycollab-mobile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with mycollab-mobile.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.esofthead.mycollab.mobile.module.project.view.parameters;

import com.esofthead.mycollab.vaadin.mvp.ScreenData;

/**
 * @author MyCollab Ltd.
 * @since 4.4.0
 */
public class ProjectScreenData {
	public static class Goto extends ScreenData<Integer> {

		public Goto(Integer params) {
			super(params);
		}
	}

	public static class Edit extends ScreenData<Integer> {

		public Edit(Integer params) {
			super(params);
		}
	}

	public static class ViewActivities extends ScreenData<Object> {
		public ViewActivities() {
			super(null);
		}
	}

	public static class GotoDashboard extends ScreenData<Object> {
		public GotoDashboard() {
			super(null);
		}
	}

	public static class AllActivities extends ScreenData<Object> {
		public AllActivities() {
			super(null);
		}
	}
}
