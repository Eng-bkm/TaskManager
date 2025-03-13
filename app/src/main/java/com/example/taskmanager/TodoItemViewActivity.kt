package com.example.taskmanager

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.taskmanager.databinding.ActivityTodoItemViewBinding

class TodoItemViewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTodoItemViewBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTodoItemViewBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
}