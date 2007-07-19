/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 * 
 * Contributor(s):
 * 
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

/*
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the "License").  You may not use this file except
 * in compliance with the License.
 * 
 * You can obtain a copy of the license at
 * https://jwsdp.dev.java.net/CDDLv1.0.html
 * See the License for the specific language governing
 * permissions and limitations under the License.
 * 
 * When distributing Covered Code, include this CDDL
 * HEADER in each file and include the License file at
 * https://jwsdp.dev.java.net/CDDLv1.0.html  If applicable,
 * add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your
 * own identifying information: Portions Copyright [yyyy]
 * [name of copyright owner]
 */
package com.sun.xml.xsom.impl;

import com.sun.xml.xsom.XSFacet;
import com.sun.xml.xsom.XSListSimpleType;
import com.sun.xml.xsom.XSSimpleType;
import com.sun.xml.xsom.XSVariety;
import com.sun.xml.xsom.impl.parser.SchemaDocumentImpl;
import com.sun.xml.xsom.visitor.XSSimpleTypeFunction;
import com.sun.xml.xsom.visitor.XSSimpleTypeVisitor;
import org.xml.sax.Locator;

import java.util.Set;

public class ListSimpleTypeImpl extends SimpleTypeImpl implements XSListSimpleType
{
    public ListSimpleTypeImpl( SchemaDocumentImpl _parent,
                               AnnotationImpl _annon, Locator _loc, ForeignAttributesImpl _fa,
                               String _name, boolean _anonymous, Set<XSVariety> finalSet,
                               Ref.SimpleType _itemType ) {

        super(_parent,_annon,_loc,_fa,_name,_anonymous, finalSet,
            _parent.getSchema().parent.anySimpleType);

        this.itemType = _itemType;
    }

    private final Ref.SimpleType itemType;
    public XSSimpleType getItemType() { return itemType.getType(); }

    public void visit( XSSimpleTypeVisitor visitor ) {
        visitor.listSimpleType(this);
    }
    public Object apply( XSSimpleTypeFunction function ) {
        return function.listSimpleType(this);
    }

    // list type by itself doesn't have any facet. */
    public XSFacet getFacet( String name ) { return null; }

    public XSVariety getVariety() { return XSVariety.LIST; }

    public XSSimpleType getPrimitiveType() { return null; }

    public XSListSimpleType getBaseListType() {return this;}

    public boolean isList() { return true; }
    public XSListSimpleType asList() { return this; }
}
