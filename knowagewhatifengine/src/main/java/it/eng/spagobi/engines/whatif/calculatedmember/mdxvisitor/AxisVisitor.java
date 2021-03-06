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
package it.eng.spagobi.engines.whatif.calculatedmember.mdxvisitor;

import org.olap4j.mdx.AxisNode;
import org.olap4j.mdx.CallNode;
import org.olap4j.mdx.CubeNode;
import org.olap4j.mdx.DimensionNode;
import org.olap4j.mdx.DrillThroughNode;
import org.olap4j.mdx.HierarchyNode;
import org.olap4j.mdx.IdentifierNode;
import org.olap4j.mdx.LevelNode;
import org.olap4j.mdx.LiteralNode;
import org.olap4j.mdx.MemberNode;
import org.olap4j.mdx.ParameterNode;
import org.olap4j.mdx.ParseTreeNode;
import org.olap4j.mdx.PropertyValueNode;
import org.olap4j.mdx.SelectNode;
import org.olap4j.mdx.WithMemberNode;
import org.olap4j.mdx.WithSetNode;
import org.olap4j.metadata.Member;

import it.eng.spagobi.engines.whatif.calculatedmember.cfinjector.CFInjector;
import it.eng.spagobi.engines.whatif.calculatedmember.cfinjector.factory.CFInjectorFactory;

/**
 * @author Dragan Pirkovic
 *
 */
public class AxisVisitor extends AbstractVisitor {

	/**
	 * @param member
	 * @param nodoCalcolato
	 */
	public AxisVisitor(Member member, IdentifierNode nodoCalcolato) {
		super(member, nodoCalcolato);

	}

	@Override
	public ParseTreeNode visit(SelectNode arg0) {

		return null;
	}

	@Override
	public ParseTreeNode visit(AxisNode arg0) {

		return null;
	}

	@Override
	public ParseTreeNode visit(WithMemberNode arg0) {

		return null;
	}

	@Override
	public ParseTreeNode visit(WithSetNode arg0) {

		return null;
	}

	@Override
	public ParseTreeNode visit(CallNode callNode) {
		//
		CFInjector cfInjector = CFInjectorFactory.getCFInjector(callNode.getSyntax().name(), callNode);
		if (cfInjector != null) {
			cfInjector.injectField(parentMember, calculatedNode);
		}

		return null;
	}

	@Override
	public ParseTreeNode visit(IdentifierNode arg0) {

		return null;
	}

	@Override
	public ParseTreeNode visit(ParameterNode arg0) {

		return null;
	}

	@Override
	public ParseTreeNode visit(CubeNode arg0) {

		return null;
	}

	@Override
	public ParseTreeNode visit(DimensionNode arg0) {

		return null;
	}

	@Override
	public ParseTreeNode visit(HierarchyNode arg0) {

		return null;
	}

	@Override
	public ParseTreeNode visit(LevelNode arg0) {

		return null;
	}

	@Override
	public ParseTreeNode visit(MemberNode arg0) {

		return null;
	}

	@Override
	public ParseTreeNode visit(LiteralNode arg0) {

		return null;
	}

	@Override
	public ParseTreeNode visit(PropertyValueNode arg0) {

		return null;
	}

	@Override
	public ParseTreeNode visit(DrillThroughNode arg0) {

		return null;
	}

}
