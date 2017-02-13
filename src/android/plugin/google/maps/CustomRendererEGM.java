package plugin.google.maps;

import android.content.Context;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.ImageView;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.*;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import plugin.google.maps.clustering.clustering.Cluster;
import plugin.google.maps.clustering.clustering.ClusterManager;
import plugin.google.maps.clustering.clustering.view.DefaultClusterRenderer;
import plugin.google.maps.clustering.ui.IconGenerator;
import plugin.google.maps.clustering.ui.SquareTextView;

import java.io.IOException;
import java.io.InputStream;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Set;

/**
 * Created by christian on 08.05.15.
 */
public class CustomRendererEGM extends DefaultClusterRenderer<ClusterItemEGM> /*implements GoogleMap.OnCameraChangeListener*/ {

	private final String TAG = "GoogleMapsPlugin";

	private final IconGenerator clusterIconGenerator;
	private ShapeDrawable mColoredCircleBackground;
	private final float mDensity;
	private BitmapDescriptor iconBackground;
	private HashMap<String, BitmapDescriptor> clusterCache;

	private HashMap<String, BitmapDescriptor> imagesCache = new HashMap<String, BitmapDescriptor>();

    private Context ctx;
	private static final int MIN_CLUSTER_SIZE = 2;

	private final int clusterdiameter = 20;

	public CustomRendererEGM(Context context, GoogleMap map, ClusterManager<ClusterItemEGM> clusterManager) {
		super(context, map, clusterManager);

        this.ctx = context;

		mDensity = context.getResources().getDisplayMetrics().density;

//		Log.d("GoogleMapsPlugin", "DENSITY = " + mDensity);

		clusterCache = new HashMap<String, BitmapDescriptor>();

		clusterIconGenerator = new IconGenerator(context);
		clusterIconGenerator.setContentView(makeSquareTextView(context));

		clusterIconGenerator.setTextAppearance(ctx.getResources().getIdentifier("ClusterIcon_TextAppearance", "style", ctx.getPackageName()));

//		clusterIconGenerator.setBackground(makeClusterBackground());

		int dynamicDiameter = (int)mDensity * clusterdiameter;

		clusterIconGenerator.setContentPadding(dynamicDiameter,dynamicDiameter,dynamicDiameter,dynamicDiameter);
		clusterIconGenerator.setColor(Color.argb(255, 243, 146, 0));
		iconBackground = BitmapDescriptorFactory.fromBitmap(clusterIconGenerator.makeIcon() );
	}

	@Override
	protected void onBeforeClusterItemRendered(ClusterItemEGM item, final MarkerOptions markerOptions) {
		markerOptions.title(item.getOptions().getTitle());
		markerOptions.snippet(item.getOptions().getSnippet());
//		markerOptions.visible(item.getOptions().isVisible());
		markerOptions.draggable(item.getOptions().isDraggable());
		markerOptions.rotation(item.getOptions().getRotation());
		markerOptions.flat(item.getOptions().isFlat());
		markerOptions.alpha(item.getOptions().getAlpha());
		markerOptions.anchor(0.5f, 1f);
		markerOptions.icon(item.getOptions().getIcon());

		if (item.getProperties() != null) {
			JSONObject properties = item.getProperties();


		}
	}

	@Override
	protected void onClusterItemRendered(ClusterItemEGM clusterItem, final Marker marker) {
		super.onClusterItemRendered(clusterItem, marker);

		if (clusterItem.getProperties() != null) {
			JSONObject props = clusterItem.getProperties();

			if (props.has("icon")) {
				try {
					final String url = props.optJSONObject("icon").getString("url");
					final Bundle bundle = new Bundle();
					bundle.putString("url", url);

					if(imagesCache.containsKey(url)) {
						marker.setIcon(imagesCache.get(url));
					} else {
						// Add the size if coming
						try {
							bundle.putBundle("size", PluginUtil.Json2Bundle(props.getJSONObject("icon").getJSONObject("size")));
						}catch( Exception e ){}

						marker.setVisible(false);
						PluginMarker.setIconResource(bundle, new PluginAsyncInterface() {

							@Override
							public void onPostExecute(Object object) {
								try {
									imagesCache.put(url, (BitmapDescriptor) object);
									marker.setIcon(imagesCache.get(url));

									marker.setVisible(true);
								} catch (Throwable t) {
									t.printStackTrace();
								}
							}

							@Override
							public void onError(String errorMsg) {
							}

						});
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}

		String id = "marker_" + marker.getId();
		if (mClusterManager.getMarkerCollection().getMarkerProperties("marker_property_" + marker.getId()) == null) {

			try {

				mClusterManager.getMarkerCollection().addPropeties(clusterItem.getProperties(), marker, clusterItem.getOptions());
			} catch (JSONException e) {

				Log.e(TAG, "JSONException Custom Cluster Renderer");

				e.printStackTrace();
			}
		}

	}

	@Override
	protected void onBeforeClusterRendered(Cluster<ClusterItemEGM> cluster, MarkerOptions markerOptions) {

		BitmapDescriptor bitmap = clusterCache.get(Integer.toString(cluster.getSize()));

		if (bitmap == null) {
			bitmap = BitmapDescriptorFactory.fromBitmap(clusterIconGenerator.makeIcon(Integer.toString(cluster.getItems().size())) );
			clusterCache.put(Integer.toString(cluster.getSize()), bitmap);
		}
		markerOptions.anchor(0.5f, 0.5f);
		markerOptions.icon(bitmap);
	}

	/**
     * Determine whether the cluster should be rendered as individual markers or a cluster.
     */
	@Override
	protected boolean shouldRenderAsCluster(Cluster<ClusterItemEGM> cluster) {
		return cluster.getSize() >= MIN_CLUSTER_SIZE;
	}

	private LayerDrawable makeClusterBackground() {

		int clusterRadius = clusterdiameter/2;

		mColoredCircleBackground = new ShapeDrawable(new OvalShape());
		ShapeDrawable outline = new ShapeDrawable(new OvalShape());
		outline.getPaint().setColor(Color.argb(30, 255, 255, 255));
		outline.setPadding(clusterRadius, clusterRadius, clusterRadius, clusterRadius);
		LayerDrawable background = new LayerDrawable(new Drawable[]{outline, mColoredCircleBackground});
		int strokeWidth = (int) (mDensity * 10);
		background.setLayerInset(1, strokeWidth, strokeWidth, strokeWidth, strokeWidth);
		return background;
	}

	private SquareTextView makeSquareTextView(Context context) {
		SquareTextView squareTextView = new SquareTextView(context);
		ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		squareTextView.setLayoutParams(layoutParams);
		squareTextView.setId(ctx.getResources().getIdentifier("text", "id", ctx.getPackageName()));
  		squareTextView.setTextColor(Color.WHITE);

		int twelveDpi = (int) (12 * mDensity);
		squareTextView.setPadding(twelveDpi, twelveDpi, twelveDpi, twelveDpi);
		return squareTextView;
	}


//	@Override
//	public void onCameraChange(CameraPosition cameraPosition) {
//
//		Log.d("GoogleMapsPlugin", "onCameraChange");
//	}
}
