package com.sanjay.imgur.paging.datasource

import androidx.lifecycle.MutableLiveData
import androidx.paging.PageKeyedDataSource
import com.sanjay.imgur.BuildConfig
import com.sanjay.imgur.constants.State
import com.sanjay.imgur.data.repository.ImgurRepository
import com.sanjay.imgur.data.repository.remote.model.ImgurImage
import com.sanjay.imgur.extensions.addToCompositeDisposable
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.functions.Action
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

class ImgurSearchPagingDataSource @Inject constructor(private val repository: ImgurRepository) :
    PageKeyedDataSource<Int, ImgurImage>() {
    val disposable = CompositeDisposable()
    //LiveData object for state
    var state = MutableLiveData<State>()
    var searchQuery = MutableLiveData<String>()
    //Completable required for retrying the API call which gets failed due to any error like no internet
    private var retryCompletable: Completable? = null

    /**
     * Creating the observable for specific page to call the API
     */
    private fun setRetry(action: Action?) {
        retryCompletable = if (action == null) null else Completable.fromAction(action)
    }

    //Retrying the API call
    fun retry() {
        if (retryCompletable != null) {
            retryCompletable!!
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe().addToCompositeDisposable(disposable)
        }
    }

    private fun updateState(state: State) {
        this.state.postValue(state)
    }

    override fun loadInitial(
        params: LoadInitialParams<Int>,
        callback: LoadInitialCallback<Int, ImgurImage>
    ) {
        updateState(State.LOADING)
        val currentPage = 1
        val nextPage = currentPage + 1
        //Call api
        repository.searchImages(BuildConfig.API_KEY, searchQuery.value!!, currentPage, params.requestedLoadSize)
            .subscribe(
                { images ->
                    updateState(State.DONE)
                    callback.onResult(images, null, nextPage)

                },
                {
                    updateState(State.ERROR)
                    setRetry(Action { loadInitial(params, callback) })
                }
            ).addToCompositeDisposable(disposable)
    }

    override fun loadAfter(params: LoadParams<Int>, callback: LoadCallback<Int, ImgurImage>) {
        updateState(State.LOADING)
        val currentPage = params.key
        val nextPage = currentPage + 1
        //Call api
        repository.searchImages(BuildConfig.API_KEY, searchQuery.value!!, currentPage, params.requestedLoadSize)
            .subscribe(
                { images ->
                    updateState(State.DONE)
                    callback.onResult(images, nextPage)

                },
                {
                    updateState(State.ERROR)
                    setRetry(Action { loadAfter(params, callback) })
                }
            ).addToCompositeDisposable(disposable)
    }

    override fun loadBefore(params: LoadParams<Int>, callback: LoadCallback<Int, ImgurImage>) {
    }
}