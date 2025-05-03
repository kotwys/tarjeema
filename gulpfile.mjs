// -*- mode: javascript -*-
import { src, dest, watch } from 'gulp';
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
