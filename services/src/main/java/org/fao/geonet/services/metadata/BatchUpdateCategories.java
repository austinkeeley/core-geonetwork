//=============================================================================
//===	Copyright (C) 2001-2007 Food and Agriculture Organization of the
//===	United Nations (FAO-UN), United Nations World Food Programme (WFP)
//===	and United Nations Environment Programme (UNEP)
//===
//===	This program is free software; you can redistribute it and/or modify
//===	it under the terms of the GNU General Public License as published by
//===	the Free Software Foundation; either version 2 of the License, or (at
//===	your option) any later version.
//===
//===	This program is distributed in the hope that it will be useful, but
//===	WITHOUT ANY WARRANTY; without even the implied warranty of
//===	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
//===	General Public License for more details.
//===
//===	You should have received a copy of the GNU General Public License
//===	along with this program; if not, write to the Free Software
//===	Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301, USA
//===
//===	Contact: Jeroen Ticheler - FAO - Viale delle Terme di Caracalla 2,
//===	Rome - Italy. email: geonetwork@osgeo.org
//==============================================================================

package org.fao.geonet.services.metadata;

import jeeves.constants.Jeeves;
import jeeves.server.ServiceConfig;
import jeeves.server.UserSession;
import jeeves.server.context.ServiceContext;
import org.fao.geonet.GeonetContext;
import org.fao.geonet.constants.Geonet;
import org.fao.geonet.domain.Metadata;
import org.fao.geonet.domain.MetadataCategory;
import org.fao.geonet.kernel.AccessManager;
import org.fao.geonet.kernel.DataManager;
import org.fao.geonet.kernel.SelectionManager;
import org.fao.geonet.repository.MetadataCategoryRepository;
import org.fao.geonet.repository.MetadataRepository;
import org.fao.geonet.services.NotInReadOnlyModeService;
import org.jdom.Element;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;


/**
 * Assigns categories to metadata.
 */
public class BatchUpdateCategories extends NotInReadOnlyModeService {
	//--------------------------------------------------------------------------
	//---
	//--- Init
	//---
	//--------------------------------------------------------------------------

	public void init(String appPath, ServiceConfig params) throws Exception {
        super.init(appPath, params);
    }

	//--------------------------------------------------------------------------
	//---
	//--- Service
	//---
	//--------------------------------------------------------------------------

	public Element serviceSpecificExec(Element params, ServiceContext context) throws Exception
	{
		GeonetContext gc = (GeonetContext) context.getHandlerContext(Geonet.CONTEXT_NAME);

		DataManager dm = gc.getBean(DataManager.class);
		AccessManager accessMan = gc.getBean(AccessManager.class);
		UserSession us = context.getUserSession();


		context.info("Get selected metadata");
		SelectionManager sm = SelectionManager.getManager(us);

		Set<Integer> metadata = new HashSet<Integer>();
		Set<Integer> notFound = new HashSet<Integer>();
		Set<Integer> notOwner = new HashSet<Integer>();

		synchronized(sm.getSelection("metadata")) {
		for (Iterator<String> iter = sm.getSelection("metadata").iterator(); iter.hasNext();) {
			String uuid = (String) iter.next();
			String id   = dm.getMetadataId(uuid);
								
			//--- check access

            final MetadataRepository metadataRepository = context.getBean(MetadataRepository.class);
            Metadata info = metadataRepository.findOne(id);
			if (info == null) {
				notFound.add(Integer.valueOf(id));
			} else if (!accessMan.isOwner(context, id)) {
				notOwner.add(Integer.valueOf(id));
			} else {

				//--- remove old operations
                info.getCategories().clear();

				//--- set new ones
				@SuppressWarnings("unchecked")
                List<Element> list = params.getChildren();

                final MetadataCategoryRepository categoryRepository = context.getBean(MetadataCategoryRepository.class);
                for (Element el : list) {
					String name = el.getName();

					if (name.startsWith("_"))  {
                        final MetadataCategory category = categoryRepository.findOne(Integer.valueOf(name.substring(1)));
                        if (category != null) {
                            info.getCategories().add(category);
                        } else {
                            context.warning("Unable to find category with name: "+name.substring(1));
                        }
                    }
				}

                metadataRepository.save(info);
				metadata.add(Integer.valueOf(id));
			}
		}
		}

        context.getBean(DataManager.class).flush();

        //--- reindex metadata
		context.info("Re-indexing metadata");
		BatchOpsMetadataReindexer r = new BatchOpsMetadataReindexer(dm, metadata);
		r.process();

		// -- for the moment just return the sizes - we could return the ids
		// -- at a later stage for some sort of result display
		return new Element(Jeeves.Elem.RESPONSE)
						.addContent(new Element("done")    .setText(metadata.size()+""))
						.addContent(new Element("notOwner").setText(notOwner.size()+""))
						.addContent(new Element("notFound").setText(notFound.size()+""));
	}
}