// -*- mode: javascript -*-
import { src, dest, watch } from 'gulp';
import filter from 'gulp-filter';
import svgSprite from 'gulp-svg-sprite';
import * as sass from 'sass';
import gulpSass from 'gulp-sass';

const sassFactory = gulpSass(sass);

export const styles = () => src('styles/index.scss')
  .pipe(sassFactory())
  .pipe(dest('resources/public/'));

export const watchStyles = () => {
  watch('styles/**/*.scss', {
    ignoreInitial: false,
  }, styles);
}

export const icons = () => src('node_modules/remixicon/**/*.svg')
  .pipe(filter(f => [
    'checkbox-blank-fill',
    'delete-bin-line',
    'thumb-up-line',
    'send-plane-2-line',
    'thumb-down-line',
    'check-line',
    'key-2-line',
  ].includes(f.stem)))
  .pipe(svgSprite({
    shape: {
      id: {
        separator: '',
      },
    },
    mode: {
      symbol: {
        sprite: 'icons.svg',
        dest: '.',
      },
    },
  }))
  .pipe(dest('resources/public/'));
