/*
 * Knowage, Open Source Business Intelligence suite
 * Copyright (C) 2016 Engineering Ingegneria Informatica S.p.A.
 * 
 * Knowage is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knowage is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package it.eng.spagobi.engines.commonj;

import it.eng.spagobi.engines.commonj.runtime.WorksRepository;
import it.eng.spagobi.tools.dataset.bo.IDataSet;
import it.eng.spagobi.utilities.engines.SpagoBIEngineException;

import java.io.File;

public class CommonjEngine {

	private static CommonjEngineConfig config;

	private static WorksRepository worksRepository;

	static {
		CommonjEngine.config = CommonjEngineConfig.getInstance();

		File rrRootDir = CommonjEngineConfig.getInstance().getWorksRepositoryRootDir();
		CommonjEngine.setWorksRepository(new WorksRepository(rrRootDir));
	}

	public static WorksRepository getWorksRepository() throws SpagoBIEngineException {
		if (worksRepository == null || !worksRepository.getRootDir().exists()) {
			throw new SpagoBIEngineException("Works-Repository not available", "repository.not.available");
		}
		return CommonjEngine.worksRepository;
	}

	public IDataSet getDataSet() {
		return null;
	}

	private static void setWorksRepository(WorksRepository worksRepository) {
		CommonjEngine.worksRepository = worksRepository;
	}

	public static CommonjEngineConfig getConfig() {
		return CommonjEngine.config;
	}

}
