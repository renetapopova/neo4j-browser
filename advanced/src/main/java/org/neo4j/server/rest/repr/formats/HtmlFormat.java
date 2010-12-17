package org.neo4j.server.rest.repr.formats;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.neo4j.graphdb.RelationshipType;
import org.neo4j.server.database.DatabaseBlockedException;
import org.neo4j.server.rest.domain.HtmlHelper;
import org.neo4j.server.rest.repr.BadInputException;
import org.neo4j.server.rest.repr.ListWriter;
import org.neo4j.server.rest.repr.MappingWriter;
import org.neo4j.server.rest.repr.Representation;
import org.neo4j.server.rest.repr.RepresentationFormat;

public class HtmlFormat extends RepresentationFormat
{
    public HtmlFormat()
    {
        super( MediaType.TEXT_HTML_TYPE );
    }

    // FIXME: needed by noderep
    private static Iterable<RelationshipType> relationshipTypes = Collections.emptyList();

    private enum MappingTemplate
    {
        NODE( Representation.NODE )
        {
            @Override
            String render( Map<String, Object> serialized )
            {
                String javascript = "";
                StringBuilder builder = HtmlHelper.start( HtmlHelper.ObjectType.NODE,
                        javascript );
                HtmlHelper.append(
                        builder,
                        Collections.singletonMap( "data", serialized.get( "data" ) ),
                        HtmlHelper.ObjectType.NODE );
                builder.append( "<form action='javascript:neo4jHtmlBrowse.getRelationships();'><fieldset><legend>Get relationships</legend>\n" );
                builder.append( "<label for='direction'>with direction</label>\n"
                        + "<select id='direction'>" );
                builder.append( "<option value='" ).append( serialized.get( "all typed relationships" ) ).append( "'>all</option>" );
                builder.append( "<option value='" ).append( serialized.get( "incoming typed relationships" ) ).append( "'>in</option>" );
                builder.append( "<option value='" ).append( serialized.get( "outgoing typed relationships" ) ).append( "'>out</option>" );
                builder.append( "</select>\n" );
                builder.append( "<label for='types'>for type(s)</label><select id='types' multiple='multiple'>" );

                try
                {
                    for ( RelationshipType type : relationshipTypes )
                    {
                        builder.append( "<option selected='selected' value='" ).append( type.name() ).append( "'>" ).append( type.name() ).append( "</option>" );
                    }
                }
                catch ( DatabaseBlockedException e )
                {
                    throw new RuntimeException(
                            "Unable to render, database is blocked, see nested exception.",
                            e );
                }
                builder.append( "</select>\n" );
                builder.append( "<button>Get</button>\n" );
                builder.append( "</fieldset></form>\n" );

                return HtmlHelper.end( builder );
            }
        },
        RELATIONSHIP( Representation.RELATIONSHIP )
        {
            @Override
            String render( Map<String, Object> serialized )
            {
                Map<Object, Object> map = new LinkedHashMap<Object, Object>();
                transfer( serialized, map, "type", "data", "start", "end" );
                return HtmlHelper.from( map, HtmlHelper.ObjectType.RELATIONSHIP );
            }
        },
        NODE_INDEXES( Representation.NODE_INDEXES )
        {
            @Override
            String render( Map<String, Object> serialized )
            {
                return renderIndex( serialized );
            }
        },
        RELATIONSHIP_INDEXES( Representation.RELATIONSHIP_INDEXES )
        {
            @Override
            String render( Map<String, Object> serialized )
            {
                return renderIndex( serialized );
            }
        },
        GRAPHDB( Representation.GRAPHDB )
        {
            @Override
            String render( Map<String, Object> serialized )
            {
                Map<Object, Object> map = new HashMap<Object, Object>();
                transfer( serialized, map, "index", "reference node" );
                return HtmlHelper.from( map, HtmlHelper.ObjectType.ROOT );
            }
        },
        EXCEPTION( Representation.EXCEPTION )
        {
            @Override
            String render( Map<String, Object> serialized )
            {
                StringBuilder entity = new StringBuilder( "<html>" );
                entity.append( "<head><title>Error</title></head><body>" );
                Object subjectOrNull = serialized.get( "message" );
                if ( subjectOrNull != null )
                {
                    entity.append( "<p><pre>" ).append( subjectOrNull ).append( "</pre></p>" );
                }
                entity.append( "<p><pre>" ).append( serialized.get( "exception" ) );
                List<Object> tb = (List<Object>) serialized.get( "stacktrace" );
                if ( tb != null )
                {
                    for ( Object el : tb )
                        entity.append( "\n\tat " + el );
                }
                entity.append( "</pre></p>" ).append( "</body></html>" );
                return entity.toString();
            }
        };
        private final String key;
        private MappingTemplate( String key )
        {
            this.key = key;
        }

        static final Map<String, MappingTemplate> TEMPLATES = new HashMap<String, MappingTemplate>();
        static
        {
            for ( MappingTemplate template : values() )
                TEMPLATES.put( template.key, template );
        }

        abstract String render( Map<String, Object> data );
    }


    private enum ListTemplate
    {
        NODES
        {
            @Override
            String render( List<Object> data )
            {
                StringBuilder builder = HtmlHelper.start( "Index hits", null );
                if ( data.isEmpty() )
                {
                    HtmlHelper.appendMessage( builder, "No index hits" );
                    return HtmlHelper.end( builder );
                }
                else
                {
                    for ( Map<?, ?> serialized : (List<Map<?, ?>>) (List<?>) data )
                    {
                        Map<Object, Object> map = new LinkedHashMap<Object, Object>();
                        transfer( serialized, map, "self", "data" );
                        HtmlHelper.append( builder, map, HtmlHelper.ObjectType.NODE );
                    }
                    return HtmlHelper.end( builder );
                }
            }
        },
        RELATIONSHIPS
        {
            @Override
            String render( List<Object> data )
            {
                if ( data.isEmpty() )
                {
                    StringBuilder builder = HtmlHelper.start( HtmlHelper.ObjectType.RELATIONSHIP,
                            null );
                    HtmlHelper.appendMessage( builder, "No relationships found" );
                    return HtmlHelper.end( builder );
                }
                else
                {
                    Collection<Object> list = new ArrayList<Object>();
                    for ( Map<?, ?> serialized : (List<Map<?, ?>>) (List<?>) data )
                    {
                        Map<Object, Object> map = new LinkedHashMap<Object, Object>();
                        transfer( serialized, map, "self", "type", "data", "start", "end" );
                        list.add( map );
                    }
                    return HtmlHelper.from( list, HtmlHelper.ObjectType.RELATIONSHIP );
                }
            }
        };

        abstract String render( List<Object> data );
    }

    private static void transfer( Map<?, ?> from, Map<Object, Object> to, String... keys )
    {
        for ( String key : keys )
        {
            Object value = from.get( key );
            if ( value != null )
            {
                to.put( key, value );
            }
        }
    }

    private static String renderIndex( Map<String, Object> serialized )
    {
        String javascript = "";
        StringBuilder builder = HtmlHelper.start( HtmlHelper.ObjectType.INDEX_ROOT, javascript );
        int counter = 0;
        for ( String indexName : serialized.keySet() )
        {
            Map<?, ?> indexMapObject = (Map<?, ?>) serialized.get( indexName );
            builder.append( "<ul>" );
            {
                builder.append( "<li>" );
                Map<?, ?> indexMap = (Map<?, ?>) indexMapObject;
                String keyId = "key_" + counter;
                String valueId = "value_" + counter;
                builder.append( "<form action='javascript:neo4jHtmlBrowse.search(\"" ).append(
                        indexMap.get( "template" ) ).append( "\",\"" ).append( keyId ).append(
                        "\",\"" ).append( valueId ).append( "\");'><fieldset><legend>" ).append(
                        indexMap.get( "type" ) ).append( "</legend>\n" );
                builder.append( "<label for='" ).append( keyId ).append( "'>Key</label><input id='" ).append(
                        keyId ).append( "'>\n" );
                builder.append( "<label for='" ).append( valueId ).append(
                        "'>Value</label><input id='" ).append( valueId ).append( "'>\n" );
                builder.append( "<button>Search</button>\n" );
                builder.append( "</fieldset></form>\n" );
                builder.append( "</li>\n" );
                counter++;
            }
            builder.append( "</ul>" );
        }
        return HtmlHelper.end( builder );
    }

    private static class HtmlMap extends MapWrappingWriter
    {
        private final MappingTemplate template;

        public HtmlMap( MappingTemplate template )
        {
            super( new HashMap<String, Object>() );
            this.template = template;
        }

        String complete()
        {
            return template.render( this.data );
        }
    }

    private static class HtmlList extends ListWrappingWriter
    {
        private final ListTemplate template;

        public HtmlList( ListTemplate template )
        {
            super( new ArrayList<Object>() );
            this.template = template;
        }

        String complete()
        {
            return template.render( this.data );
        }
    }

    @Override
    protected String complete( ListWriter serializer )
    {
        return ( (HtmlList) serializer ).complete();
    }

    @Override
    protected String complete( MappingWriter serializer )
    {
        return ( (HtmlMap) serializer ).complete();
    }

    @Override
    protected ListWriter serializeList( String type )
    {
        if ( Representation.NODE_LIST.equals( type ) )
        {
            return new HtmlList( ListTemplate.NODES );
        }
        else if ( Representation.RELATIONSHIP_LIST.equals( type ) )
        {
            return new HtmlList( ListTemplate.RELATIONSHIPS );
        }
        else
        {
            throw new WebApplicationException(
                    Response.status( Response.Status.NOT_ACCEPTABLE ).entity(
                            "Cannot represent \"" + type + "\" as html" ).build() );
        }
    }

    @Override
    protected MappingWriter serializeMapping( String type )
    {
        MappingTemplate template = MappingTemplate.TEMPLATES.get( type );
        if ( template == null )
        {
            throw new WebApplicationException(
                    Response.status( Response.Status.NOT_ACCEPTABLE ).entity(
                            "Cannot represent \"" + type + "\" as html" ).build() );
        }
        return new HtmlMap( template );
    }

    @Override
    protected String serializeValue( String type, Object value )
    {
        throw new WebApplicationException(
                Response.status( Response.Status.NOT_ACCEPTABLE ).entity(
                        "Cannot represent \"" + type + "\" as html" ).build() );
    }

    @Override
    public List<Object> readList( String input ) throws BadInputException
    {
        throw new WebApplicationException(
                Response.status( Response.Status.UNSUPPORTED_MEDIA_TYPE ).entity(
                        "Cannot read html" ).build() );
    }

    @Override
    public Map<String, Object> readMap( String input ) throws BadInputException
    {
        throw new WebApplicationException(
                Response.status( Response.Status.UNSUPPORTED_MEDIA_TYPE ).entity(
                        "Cannot read html" ).build() );
    }

    @Override
    public URI readUri( String input ) throws BadInputException
    {
        throw new WebApplicationException(
                Response.status( Response.Status.UNSUPPORTED_MEDIA_TYPE ).entity(
                        "Cannot read html" ).build() );
    }

    @Override
    public Object readValue( String input ) throws BadInputException
    {
        throw new WebApplicationException(
                Response.status( Response.Status.UNSUPPORTED_MEDIA_TYPE ).entity(
                        "Cannot read html" ).build() );
    }
}
