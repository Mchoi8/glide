package com.bumptech.glide;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.test.core.app.ApplicationProvider;
import com.bumptech.glide.GlideTest.CallSizeReady;
import com.bumptech.glide.load.Key;
import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.engine.cache.DiskCache;
import com.bumptech.glide.load.engine.cache.MemoryCache;
import com.bumptech.glide.load.engine.executor.GlideExecutor;
import com.bumptech.glide.load.engine.executor.MockGlideExecutor;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;
import com.bumptech.glide.manager.Lifecycle;
import com.bumptech.glide.manager.RequestManagerTreeNode;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.SizeReadyCallback;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;
import com.bumptech.glide.tests.Util;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;


public class GlideAppTesting {

  @SuppressWarnings("rawtypes")
  @Mock
  private Target target;

  @Mock private DiskCache.Factory diskCacheFactory;
  @Mock private DiskCache diskCache;
  @Mock private MemoryCache memoryCache;
  @Mock private Handler bgHandler;
  @Mock private Lifecycle lifecycle;
  @Mock private RequestManagerTreeNode treeNode;
  @Mock private BitmapPool bitmapPool;

  private ImageView imageView;
  private RequestManager requestManager;
  private RequestManager requestManager1;

  private Context context;


  private static <X, Y> void registerMockModelLoader(
      Class<X> modelClass, Class<Y> dataClass, Y loadedData, Registry registry) {
    DataFetcher<Y> mockStreamFetcher = mock(DataFetcher.class);
    when(mockStreamFetcher.getDataClass()).thenReturn(dataClass);
    try {
      doAnswer(new Util.CallDataReady<>(loadedData))
          .when(mockStreamFetcher)
          .loadData(isA(Priority.class), isA(DataFetcher.DataCallback.class));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    ModelLoader<X, Y> mockUrlLoader = mock(ModelLoader.class);
    when(mockUrlLoader.buildLoadData(isA(modelClass), anyInt(), anyInt(), isA(Options.class)))
        .thenReturn(new ModelLoader.LoadData<>(mock(Key.class), mockStreamFetcher));
    when(mockUrlLoader.handles(isA(modelClass))).thenReturn(true);
    ModelLoaderFactory<X, Y> mockUrlLoaderFactory = mock(ModelLoaderFactory.class);
    when(mockUrlLoaderFactory.build(isA(MultiModelLoaderFactory.class))).thenReturn(mockUrlLoader);

    registry.replace(modelClass, dataClass, mockUrlLoaderFactory);
  }


  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    context = ApplicationProvider.getApplicationContext();

    // Run all tasks on the main thread so they complete synchronously.
    GlideExecutor executor = MockGlideExecutor.newMainThreadExecutor();
    when(diskCacheFactory.build()).thenReturn(diskCache);
    Glide.init(
        context,
        new GlideBuilder()
            .setMemoryCache(memoryCache)
            .setDiskCache(diskCacheFactory)
            .setSourceExecutor(executor)
            .setDiskCacheExecutor(executor));
    Registry registry = Glide.get(context).getRegistry();
    registerMockModelLoader(
        GlideUrl.class, InputStream.class, new ByteArrayInputStream(new byte[0]), registry);
    registerMockModelLoader(
        File.class, InputStream.class, new ByteArrayInputStream(new byte[0]), registry);
    registerMockModelLoader(
        File.class, ParcelFileDescriptor.class, mock(ParcelFileDescriptor.class), registry);
    registerMockModelLoader(File.class, ByteBuffer.class, ByteBuffer.allocate(10), registry);

    // Ensure that target's size ready callback will be called synchronously.
    imageView = new ImageView(context);
    imageView.setLayoutParams(new ViewGroup.LayoutParams(100, 100));
    imageView.layout(0, 0, 100, 100);
    doAnswer(new CallSizeReady()).when(target).getSize(isA(SizeReadyCallback.class));

    when(bgHandler.post(isA(Runnable.class)))
        .thenAnswer(
            new Answer<Boolean>() {
              @Override
              public Boolean answer(InvocationOnMock invocation) {
                Runnable runnable = (Runnable) invocation.getArguments()[0];
                runnable.run();
                return true;
              }
            });

    requestManager = new RequestManager(Glide.get(context), lifecycle, treeNode, context);
    requestManager1 = new RequestManager(Glide.get(context), lifecycle, treeNode, context);
    requestManager.resumeRequests();
  }


  @Test
  public void testOverride() {

    ColorDrawable colorDrawable = new ColorDrawable(Color.BLUE);
    requestManager
        .load(colorDrawable)
        .apply(new RequestOptions().override(0, 0))
        .into(target);

    ArgumentCaptor<Object> argumentCaptor = ArgumentCaptor.forClass(Object.class);

    Object result = argumentCaptor.getValue();

    assertThat(result).isInstanceOf(BitmapDrawable.class);
    Bitmap bitmap = ((BitmapDrawable) result).getBitmap();
    assertThat(bitmap.getWidth()).isEqualTo(0);
    assertThat(bitmap.getHeight()).isEqualTo(0);
  }

  @Test
  public void testOverride1() {
    ColorDrawable colorDrawable = new ColorDrawable(Color.BLUE);
    requestManager
        .load(colorDrawable)
        .apply(new RequestOptions().override(350, 400))
        .into(target);

    ArgumentCaptor<Object> argumentCaptor = ArgumentCaptor.forClass(Object.class);

    Object result = argumentCaptor.getValue();

    assertThat(result).isInstanceOf(BitmapDrawable.class);
    Bitmap bitmap = ((BitmapDrawable) result).getBitmap();
    assertThat(bitmap.getWidth()).isEqualTo(350);
    assertThat(bitmap.getHeight()).isEqualTo(400);
  }



  @Test
  public void testCircleCrop() {
    ColorDrawable colorDrawable = new ColorDrawable(Color.GREEN);
    requestManager
        .load(colorDrawable)
        .apply(new RequestOptions().override(100, 100).circleCrop())
        .into(target);

    ArgumentCaptor<Object> argumentCaptor = ArgumentCaptor.forClass(Object.class);

    Object result = argumentCaptor.getValue();

    assertThat(result).isInstanceOf(BitmapDrawable.class);
    Bitmap bitmap = ((BitmapDrawable) result).getBitmap();

    //Check that the crop is there
    assertThat(bitmap.getColor(0,0)).isEqualTo(Color.GREEN);
    assertThat(bitmap.getWidth()).isEqualTo(350);
    assertThat(bitmap.getHeight()).isEqualTo(400);
  }

  @Test
  public void testCircleCrop1() {
    ColorDrawable colorDrawable = new ColorDrawable(Color.YELLOW);
    requestManager
        .load(colorDrawable)
        .apply(new RequestOptions().override(0, 0)).circleCrop()
        .into(target);

    ArgumentCaptor<Object> argumentCaptor = ArgumentCaptor.forClass(Object.class);

    Object result = argumentCaptor.getValue();

    assertThat(result).isInstanceOf(BitmapDrawable.class);
    Bitmap bitmap = ((BitmapDrawable) result).getBitmap();

    //check if the image is there
    assertThat(bitmap.getColor(0,0)).isNotEqualTo(Color.YELLOW);
    assertThat(bitmap.getWidth()).isEqualTo(0);
    assertThat(bitmap.getHeight()).isEqualTo(0);
  }



  @Test
  public void testPlaceholder() {
    ColorDrawable colorDrawable1 = new ColorDrawable(Color.GREEN);

    ColorDrawable colorDrawable = new ColorDrawable(Color.BLUE);
    ColorDrawable placeholderD = new ColorDrawable(Color.BLACK);

    requestManager1
        .load(colorDrawable1)
        .apply(new RequestOptions().placeholder(placeholderD))
        .into(target);

    ArgumentCaptor<Object> argumentCaptor1 = ArgumentCaptor.forClass(Object.class);

    Object result1 = argumentCaptor1.getValue();

    assertThat(result1).isInstanceOf(BitmapDrawable.class);
    Bitmap bitmap1 = ((BitmapDrawable) result1).getBitmap();

    requestManager
        .load(colorDrawable)
        .apply(new RequestOptions().placeholder(placeholderD))
        .into(target);

    ArgumentCaptor<Object> argumentCaptor = ArgumentCaptor.forClass(Object.class);

    Object result = argumentCaptor.getValue();



    assertThat(result).isInstanceOf(BitmapDrawable.class);
    Bitmap bitmap = ((BitmapDrawable) result).getBitmap();

    //Checks if the placeholder and an example placeholder img is the same
    assertThat(bitmap.sameAs(bitmap1));
  }




  @Test
  public void testError() {
    ColorDrawable colorDrawable = new ColorDrawable(Color.BLUE);
    ColorDrawable colorDrawable1 = new ColorDrawable(Color.GREEN);


    requestManager1
        .load(colorDrawable1)
        .apply(new RequestOptions().error(colorDrawable1))
        .into(target);

    ArgumentCaptor<Object> argumentCaptor1 = ArgumentCaptor.forClass(Object.class);

    Object result1 = argumentCaptor1.getValue();

    assertThat(result1).isInstanceOf(BitmapDrawable.class);
    Bitmap bitmap1 = ((BitmapDrawable) result1).getBitmap();




    requestManager
        .load(colorDrawable)
        .apply(new RequestOptions().error(colorDrawable1))
        .into(target);

    ArgumentCaptor<Object> argumentCaptor = ArgumentCaptor.forClass(Object.class);

    Object result = argumentCaptor.getValue();

    assertThat(result).isInstanceOf(BitmapDrawable.class);
    Bitmap bitmap = ((BitmapDrawable) result).getBitmap();

    //Checks if the error and the extra example error img is the same
    assertThat(bitmap.sameAs(bitmap1));
    
  }
  

}
